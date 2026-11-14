const $ = (id) => document.getElementById(id);
let state,
  expenseLimit = 30,
  pending = false,
  currentGroup = '',
  currentAccount = null,
  csrf = null,
  authMode = 'login',
  availableGroups = [],
  routeSequence = 0;
const currencyFormatter = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });
const money = (n) => currencyFormatter.format(n / 100);
const esc = (s) =>
  String(s).replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  );
async function request(path, data) {
  if (pending) throw Error('A request is already in progress. Please wait.');
  pending = true;
  document.body.setAttribute('aria-busy', 'true');
  const buttons = [...document.querySelectorAll('button')].map((button) => [
    button,
    button.disabled,
  ]);
  buttons.forEach(([button]) => (button.disabled = true));
  try {
    const r = await secureFetch(
      '/api/' + path + '?group=' + encodeURIComponent(currentGroup),
      data ? { method: 'POST', body: data } : {},
    );
    const result = await r.json();
    if (!r.ok) throw Error(result.error || 'Something went wrong.');
    state = result;
    render();
  } finally {
    pending = false;
    document.body.removeAttribute('aria-busy');
    buttons.forEach(([button, disabled]) => (button.disabled = disabled));
    if (state) $('addExpense').disabled = !currentGroup;
    $('addMemberButton').disabled = !selectedPerson;
  }
}
function render() {
  resetMemberSearch();
  $('memberForm').hidden = !state.isOwner;
  $('demo').hidden = !currentGroup || !state.isOwner;
  $('reset').hidden = !state.isOwner;
  $('addExpense').disabled = !currentGroup;
  $('memberHint').textContent = state.isOwner
    ? 'Search registered people, select someone, and add them instantly.'
    : 'Only the group owner can add members.';
  renderPayments();
  renderResolution();
  $('showMore').hidden = state.expenses.length <= expenseLimit;
  $('total').textContent = money(state.expenses.reduce((a, e) => a + e.cents, 0));
  $('count').textContent =
    state.members.length + (state.members.length === 1 ? ' friend' : ' friends');
  $('transfers').textContent = state.transfers.length + ' transfers';
  $('crew').innerHTML =
    state.members
      .map(
        (m) =>
          `<span class="person"><span class="avatar">${esc(m.slice(0, 2).toUpperCase())}</span>${esc(m)}</span>`,
      )
      .join('') ||
    '<p class="muted">Every good trip starts with good company. Add your first friend.</p>';
  $('expenses').innerHTML =
    state.expenses
      .slice(-expenseLimit)
      .reverse()
      .map(
        (e) =>
          `<div class="expense"><div class="expense-icon">↗</div><div class="expense-info"><strong>${esc(e.title)}</strong><small>${esc(e.payer)} paid · ${e.mode === 'exact' ? 'exact amounts' : e.mode === 'percentage' ? 'percentage split' : 'equal split'} · ${e.members.length} people</small><small class="share-details">${e.members.map((m, i) => esc(m) + ': ' + money(e.shares[i])).join(' · ')}</small></div><span class="amount">${money(e.cents)}</span>${state.canDelete?.includes(e.id) ? `<button class="delete" data-delete="${esc(e.id)}" aria-label="Delete ${esc(e.title)}">×</button>` : ''}</div>`,
      )
      .join('') ||
    '<div class="empty">No expenses yet.<br>Add the first bill, or load the trip demo to take a look around.</div>';
  $('settlements').innerHTML =
    state.transfers
      .map(
        (t, index) =>
          `<div class="transfer"><span><b>${esc(t.from)}</b> → <b>${esc(t.to)}</b></span><strong>${money(t.cents)}</strong>${t.from === state.viewerMember ? `<button class="green test-pay" data-demo-pay="${index}">Try demo payment ↗</button>` : `<p class="muted small">${esc(t.from)} can make this repayment.</p>`}${state.paymentConfig?.enabled && t.from === state.viewerMember ? `<button class="test-pay" data-pay="${index}" ${!state.paymentConfig?.enabled || t.cents < 100 ? 'disabled' : ''} title="${t.cents < 100 ? 'Minimum test checkout is ₹1' : 'Sandbox checkout; no real money'}">Pay with Razorpay test ↗</button>` : ''}</div>`,
      )
      .join('') || '<div class="empty">✦ All clear.<br>Everyone is settled up.</div>';
  $('balances').innerHTML =
    state.balances
      .map(
        (b) =>
          `<div class="balance"><span class="avatar">${esc(b.name.slice(0, 2).toUpperCase())}</span><span>${esc(b.name)}</span><strong class="${b.cents < 0 ? 'negative' : 'positive'}">${b.cents > 0 ? '+' : ''}${money(b.cents)}</strong></div>`,
      )
      .join('') || '<p class="muted small">Your crew’s balances will appear here.</p>';
}
function feedback(error) {
  $('notice').textContent = error.message;
  $('notice').style.color = '#a33b32';
}
$('memberForm').onsubmit = async (event) => {
  event.preventDefault();
  if (!selectedPerson || pending) return;
  const person = selectedPerson;
  try {
    await request('members', new URLSearchParams({ username: person.username }));
    resetMemberSearch();
    $('notice').textContent =
      person.displayName + ' (@' + person.username + ') added to the group.';
    $('notice').style.color = '';
  } catch (error) {
    feedback(error);
  }
};
$('addExpense').onclick = () => {
  if (!state?.members.length) {
    feedback(Error('Create a group first, then invite your friends.'));
    $('friend').focus();
    return;
  }
  $('expenseForm').reset();
  $('formError').textContent = '';
  $('payer').innerHTML = state.members.map((m) => `<option>${esc(m)}</option>`).join('');
  $('participants').innerHTML = state.members
    .map(
      (m) =>
        `<label><input type="checkbox" name="member" value="${esc(m)}" checked>${esc(m)}</label>`,
    )
    .join('');
  renderShares();
  $('expenseDialog').showModal();
};
$('closeDialog').onclick = () => $('expenseDialog').close();
$('expenseForm').onsubmit = async (e) => {
  e.preventDefault();
  const button = e.target.querySelector('button.full');
  button.disabled = true;
  try {
    await request('expenses', expenseData());
    $('expenseDialog').close();
    $('notice').textContent = 'Expense added. Balances are up to date.';
    $('notice').style.color = '';
  } catch (error) {
    $('formError').textContent = error.message;
  } finally {
    button.disabled = false;
  }
};
$('expenses').onclick = async (e) => {
  const b = e.target.closest('[data-delete]');
  if (b && confirm('Delete this expense and recalculate balances?'))
    try {
      await request('delete', new URLSearchParams({ id: b.dataset.delete }));
    } catch (error) {
      feedback(error);
    }
};
$('demo').onclick = async () => {
  if (state?.members.length && !confirm('Replace this group with sample trip data?')) return;
  try {
    await request('demo', new URLSearchParams());
    $('notice').textContent = 'Demo loaded. Add an expense to try it out.';
    $('notice').style.color = '';
  } catch (error) {
    feedback(error);
  }
};
$('reset').onclick = async () => {
  if (!confirm('Clear all friends and expenses in this group? Other groups will stay intact.'))
    return;
  try {
    await request('reset', new URLSearchParams());
    $('notice').textContent = 'A clean slate for your next adventure.';
  } catch (error) {
    feedback(error);
  }
};

function selectedMembers() {
  return [...$('participants').querySelectorAll('input:checked')].map((i) => i.value);
}
function renderShares() {
  const mode = $('splitMode').value,
    old = new Map(
      [...$('customShares').querySelectorAll('input')].map((i) => [i.dataset.person, i.value]),
    );
  $('customShares').innerHTML =
    mode === 'equal'
      ? ''
      : selectedMembers()
          .map(
            (m) =>
              `<label class="custom-share"><span>${esc(m)} ${mode === 'exact' ? '(₹)' : '(%)'}</span><input data-person="${esc(m)}" type="number" step="0.01" min="0" max="${mode === 'exact' ? '1000000' : '100'}" value="${esc(old.get(m) || '')}" placeholder="0.00" required></label>`,
          )
          .join('');
  updateSplitSummary();
}
function updateSplitSummary() {
  const mode = $('splitMode').value,
    inputs = [...$('customShares').querySelectorAll('input')];
  if (mode === 'equal') {
    $('splitSummary').textContent = 'Split equally. Leftover paise go in the order shown.';
    return;
  }
  const units = (v) => {
    const text = String(v || '0');
    if (!/^\d+(?:\.\d{0,2})?$/.test(text)) return NaN;
    const [whole, fraction = ''] = text.split('.');
    return Number(whole) * 100 + Number(fraction.padEnd(2, '0'));
  };
  const total = inputs.reduce((a, i) => a + units(i.value), 0),
    target = mode === 'exact' ? units($('expenseForm').elements.amount.value) : 10000;
  if (!Number.isFinite(total) || !Number.isFinite(target)) {
    $('splitSummary').textContent = 'Use nonnegative values with at most two decimals.';
    return;
  }
  const diff = target - total;
  $('splitSummary').textContent =
    mode === 'exact'
      ? `${money(total)} allocated · ${money(diff)} remaining`
      : `${(total / 100).toFixed(2)}% allocated · ${(diff / 100).toFixed(2)}% remaining`;
}
function expenseData() {
  const data = new URLSearchParams(new FormData($('expenseForm')));
  for (const input of $('customShares').querySelectorAll('input'))
    data.append('share', input.value);
  return data;
}
$('splitMode').onchange = () => {
  $('customShares').innerHTML = '';
  renderShares();
};
$('participants').onchange = renderShares;
$('customShares').oninput = updateSplitSummary;
$('expenseForm').elements.amount.oninput = updateSplitSummary;

function renderResolution() {
  const select = $('resolutionPerson'),
    person = state.members.includes(select.value) ? select.value : state.members[0];
  select.innerHTML = state.members
    .map((m) => `<option value="${esc(m)}">${esc(m)}</option>`)
    .join('');
  if (!person) {
    $('resolution').innerHTML = '<p class="empty">Add friends and expenses to see the maths.</p>';
    return;
  }
  select.value = person;
  let paid = 0,
    share = 0;
  const pairs = new Map();
  for (const other of state.members)
    if (other !== person) pairs.set(other, { theyOwe: 0, youOwe: 0, items: [] });
  for (const e of state.expenses) {
    if (e.payer === person) paid += e.cents;
    const index = e.members.indexOf(person);
    if (index >= 0) share += e.shares[index];
    if (e.payer === person) {
      for (let i = 0; i < e.members.length; i++) {
        const pair = pairs.get(e.members[i]);
        if (pair && e.shares[i]) {
          pair.theyOwe += e.shares[i];
          pair.items.push(`${esc(e.title)}: ${esc(e.members[i])} owes ${money(e.shares[i])}`);
        }
      }
    } else if (index >= 0 && e.shares[index]) {
      const pair = pairs.get(e.payer);
      pair.youOwe += e.shares[index];
      pair.items.push(`${esc(e.title)}: Share owed by ${esc(person)}: ${money(e.shares[index])}`);
    }
  }
  const verified = (state.payments || []).filter(
    (p) => p.status === 'verified' || p.status === 'demo_success',
  );
  const sent = verified.filter((p) => p.from === person).reduce((sum, p) => sum + p.cents, 0);
  const received = verified.filter((p) => p.to === person).reduce((sum, p) => sum + p.cents, 0);
  const net = paid - share + sent - received;
  let html = `<div class="math-total"><div>Total paid <b>${money(paid)}</b></div><div>Minus own share <b>− ${money(share)}</b></div><div>Test repayments sent <b>+ ${money(sent)}</b></div><div>Test repayments received <b>− ${money(received)}</b></div><div class="net-line">${net >= 0 ? 'Gets back' : 'Owes'} <b>${money(Math.abs(net))}</b></div></div><h3>1. Original debts between friends</h3>`;
  const active = [...pairs].filter(([, p]) => p.theyOwe || p.youOwe);
  html +=
    active
      .map(([other, p]) => {
        const difference = p.theyOwe - p.youOwe;
        return `<details class="debt-detail"><summary>${esc(other)} <strong>${difference === 0 ? 'Even' : difference > 0 ? 'owes ' + esc(person) + ' ' + money(difference) : 'gets ' + money(-difference) + ' from ' + esc(person)}</strong></summary><div class="debt-body"><p>${esc(other)} → ${esc(person)}: <b>${money(p.theyOwe)}</b><br>${esc(person)} → ${esc(other)}: <b>${money(p.youOwe)}</b></p><p>${money(Math.max(p.theyOwe, p.youOwe))} − ${money(Math.min(p.theyOwe, p.youOwe))} = <b>${money(Math.abs(difference))}</b></p>${p.items.map((item) => `<div class="small muted">${item}</div>`).join('')}</div></details>`;
      })
      .join('') || '<p class="muted small">No debts with other friends yet.</p>';
  html +=
    '<h3 class="math-heading">2. Clear the group balances</h3><p class="muted small">The original debts above come from expenses. Successful demo and verified test repayments adjust the overall balance. The plan below combines remaining debts across friends, so someone may pay a different person from the original bill.</p>';
  let remaining = net;
  const steps = state.transfers.filter((t) => t.from === person || t.to === person);
  html +=
    steps
      .map((t) => {
        const before = remaining;
        remaining += t.from === person ? t.cents : -t.cents;
        const label = (n) =>
          n === 0 ? '₹0.00' : money(Math.abs(n)) + (n > 0 ? ' to receive' : ' to pay');
        return `<div class="resolution-step"><b>${t.from === person ? 'Pay ' + esc(t.to) : 'Receive from ' + esc(t.from)} ${money(t.cents)}</b><span>${label(before)} → ${label(remaining)}</span></div>`;
      })
      .join('') || '<p class="muted small">No payment needed.</p>';
  $('resolution').innerHTML = html;
}
$('resolutionPerson').onchange = renderResolution;

$('showMore').onclick = () => {
  expenseLimit += 30;
  render();
};

async function fetchGroups(data) {
  const response = await secureFetch('/api/groups', data ? { method: 'POST', body: data } : {});
  const result = await response.json();
  if (!response.ok) throw Error(result.error || 'Could not load groups.');
  return result;
}
function drawGroups() {
  $('groupTotal').textContent =
    availableGroups.length + ' ' + (availableGroups.length === 1 ? 'group' : 'groups');
  $('groupCards').innerHTML =
    availableGroups
      .map(
        (group, index) =>
          `<a class="group-card color-${index % 4}" href="/groups/${encodeURIComponent(group.id)}" data-group="${esc(group.id)}"><div class="group-card-top"><span class="group-icon">${esc(group.name.slice(0, 2).toUpperCase())}</span><span class="role-badge">${group.isOwner ? 'Owner' : 'Member'}</span></div><h3>${esc(group.name)}</h3><div class="group-card-bottom"><span>Expenses · splits · transactions</span><b aria-hidden="true">↗</b></div></a>`,
      )
      .join('') ||
    '<div class="panel empty-groups"><h2>Your next adventure starts here.</h2><p class="muted">Create your first group above, or accept an invitation below.</p></div>';
}
function emptyState() {
  return {
    members: [],
    expenses: [],
    payments: [],
    balances: [],
    transfers: [],
    canDelete: [],
    isOwner: false,
    paymentConfig: {},
    viewerMember: currentAccount?.username,
  };
}
async function initializeGroups() {
  const result = await fetchGroups();
  availableGroups = result.groups;
  drawGroups();
  await applyRoute();
  await loadInvitations();
}
async function applyRoute() {
  if (!currentAccount) return;
  if (pending) {
    setTimeout(() => applyRoute().catch(feedback), 100);
    return;
  }
  const sequence = ++routeSequence,
    match = location.pathname.match(/^\/groups\/([^/]+)\/?$/);
  const id = match ? decodeURIComponent(match[1]) : '';
  const group = availableGroups.find((g) => g.id === id);
  $('notice').textContent = '';
  if (!group) {
    currentGroup = '';
    state = null;
    $('groupPage').hidden = true;
    $('groupsHome').hidden = false;
    $('demo').hidden = true;
    document.title = 'Your groups — SplitSmart';
    if (match) {
      history.replaceState({}, '', '/');
      feedback(Error('This group is unavailable or you are not a member.'));
    }
    return;
  }
  $('groupsHome').hidden = true;
  $('groupPage').hidden = true;
  $('demo').hidden = true;
  currentGroup = id;
  expenseLimit = 30;
  try {
    await request('state');
    if (sequence !== routeSequence) return;
    $('groupPageTitle').textContent = group.name;
    $('groupPage').hidden = false;
    document.title = group.name + ' — SplitSmart';
  } catch (error) {
    if (sequence !== routeSequence) return;
    currentGroup = '';
    state = null;
    $('groupsHome').hidden = false;
    history.replaceState({}, '', '/');
    feedback(error);
  }
}
async function navigate(path) {
  if (pending) return;
  if (location.pathname !== path) history.pushState({}, '', path);
  await applyRoute();
  window.scrollTo({ top: 0, behavior: 'instant' });
}
$('groupCards').onclick = (event) => {
  const link = event.target.closest('[data-group]');
  if (!link || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  navigate(link.getAttribute('href')).catch(feedback);
};
$('backToGroups').onclick = (event) => {
  if (event.metaKey || event.ctrlKey) return;
  event.preventDefault();
  navigate('/').catch(feedback);
};
document.querySelector('.brand').onclick = (event) => {
  if (!currentAccount || event.metaKey || event.ctrlKey) return;
  event.preventDefault();
  navigate('/').catch(feedback);
};
window.addEventListener('popstate', () => applyRoute().catch(feedback));
$('groupForm').onsubmit = async (event) => {
  event.preventDefault();
  if (pending) return;
  const button = event.target.querySelector('button');
  button.disabled = true;
  pending = true;
  try {
    const result = await fetchGroups(new URLSearchParams(new FormData(event.target)));
    availableGroups = result.groups;
    drawGroups();
    pending = false;
    event.target.reset();
    await navigate('/groups/' + encodeURIComponent(result.selected));
    $('notice').textContent = 'New group created. Add your people to get started.';
    $('notice').style.color = '';
  } catch (error) {
    feedback(error);
  } finally {
    button.disabled = false;
    pending = false;
  }
};

function renderPayments() {
  $('paymentMode').textContent = 'DEMO PAYMENTS · No provider signup. No real money.';
  const history = state.payments || [];
  const labels = {
    demo_created: 'Demo · awaiting outcome',
    demo_success: 'Demo · succeeded',
    demo_failed: 'Demo · failed — balance unchanged',
    demo_cancelled: 'Demo · cancelled — balance unchanged',
    verified: 'Razorpay test · verified',
    created: 'Razorpay test · awaiting verification',
  };
  $('paymentHistory').innerHTML =
    [...history]
      .reverse()
      .map(
        (p) =>
          `<div class="payment-entry"><b>${esc(p.from)} → ${esc(p.to)} · ${money(p.cents)}</b><span>${esc(labels[p.status] || p.status)}</span><small>${esc(p.orderId)}</small>${p.status === 'demo_created' && p.from === state.viewerMember ? `<button data-resume-demo="${esc(p.orderId)}">Continue demo</button>` : p.status === 'created' && p.from === state.viewerMember ? `<button data-check-order="${esc(p.orderId)}" ${!state.paymentConfig?.enabled ? 'disabled' : ''}>Check payment status</button>` : ''}</div>`,
      )
      .join('') ||
    '<p class="muted small">No demo payments yet. Try one from a suggested repayment.</p>';
}
async function paymentApi(action, data, group) {
  const response = await secureFetch(
    '/api/payments/' + action + '?group=' + encodeURIComponent(group),
    { method: 'POST', body: new URLSearchParams(data) },
  );
  const result = await response.json();
  if (!response.ok) throw Error(result.error || 'Payment request failed.');
  return result;
}
let checkoutScript;
function loadCheckout() {
  if (window.Razorpay) return Promise.resolve();
  if (!checkoutScript)
    checkoutScript = new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = 'https://checkout.razorpay.com/v1/checkout.js';
      script.onload = () => (window.Razorpay ? resolve() : reject(Error('Checkout unavailable.')));
      script.onerror = () => {
        script.remove();
        checkoutScript = null;
        reject(Error('Could not load Razorpay checkout. Check your connection.'));
      };
      document.head.appendChild(script);
    });
  return checkoutScript;
}
async function razorpayCheckout(event) {
  const button = event.target.closest('[data-pay]');
  if (!button || pending) return;
  const transfer = state.transfers[Number(button.dataset.pay)],
    group = currentGroup;
  if (!transfer || transfer.from !== state.viewerMember || !state.paymentConfig?.enabled) return;
  pending = true;
  button.disabled = true;
  let finished = false;
  const release = () => {
    pending = false;
    button.disabled = false;
  };
  try {
    await loadCheckout();
    const order = await paymentApi('orders', { from: transfer.from, to: transfer.to }, group);
    const checkout = new window.Razorpay({
      key: order.keyId,
      order_id: order.orderId,
      amount: order.amount,
      currency: order.currency,
      name: 'SplitSmart · TEST',
      description: `Simulated repayment: ${order.from} to ${order.to}`,
      theme: { color: '#53723f' },
      handler: async (response) => {
        finished = true;
        try {
          state = await paymentApi('verify', response, group);
          render();
          $('notice').textContent =
            'Test payment verified. Demo balances updated. No real money moved.';
          $('notice').style.color = '';
        } catch (error) {
          feedback(error);
          try {
            const r = await secureFetch('/api/state?group=' + encodeURIComponent(group));
            if (r.ok) {
              state = await r.json();
              render();
            }
          } catch {}
        } finally {
          release();
        }
      },
      modal: {
        ondismiss: async () => {
          if (finished) return;
          finished = true;
          try {
            const r = await secureFetch('/api/state?group=' + encodeURIComponent(group));
            if (r.ok) {
              state = await r.json();
              render();
            }
            $('notice').textContent =
              'Checkout closed. Balances change only after verification. Check payment status before retrying.';
          } catch (error) {
            feedback(error);
          } finally {
            release();
          }
        },
      },
    });
    checkout.on('payment.failed', () => {
      feedback(
        Error(
          'Test payment failed. Balances are unchanged. Close checkout or retry within Razorpay.',
        ),
      );
    });
    checkout.open();
  } catch (error) {
    feedback(error);
    release();
  }
}
async function reconcilePayment(event) {
  const button = event.target.closest('[data-check-order]');
  if (!button || pending) return;
  pending = true;
  button.disabled = true;
  try {
    state = await paymentApi('reconcile', { orderId: button.dataset.checkOrder }, currentGroup);
    render();
    $('notice').textContent = 'Test payment verified. Demo balances updated.';
    $('notice').style.color = '';
  } catch (error) {
    feedback(error);
  } finally {
    pending = false;
    button.disabled = false;
  }
}

let demoOrder = null,
  demoGroup = null;
function openDemo(order, group) {
  demoOrder = order;
  demoGroup = group;
  $('demoPaymentError').textContent = '';
  $('demoPaymentParties').textContent = order.from + ' → ' + order.to;
  $('demoPaymentAmount').textContent = money(order.amount);
  $('demoPaymentDialog').showModal();
}
async function beginDemo(index) {
  if (pending) return;
  const transfer = state.transfers[index];
  if (!transfer || transfer.from !== state.viewerMember) return;
  const group = currentGroup;
  pending = true;
  try {
    const order = await paymentApi('demo-orders', { from: transfer.from, to: transfer.to }, group);
    openDemo(order, group);
  } catch (error) {
    feedback(error);
  } finally {
    pending = false;
  }
}
async function finishDemo(outcome) {
  if (pending || !demoOrder) return;
  pending = true;
  const buttons = [...$('demoPaymentDialog').querySelectorAll('button')];
  buttons.forEach((b) => (b.disabled = true));
  try {
    state = await paymentApi('demo-complete', { orderId: demoOrder.orderId, outcome }, demoGroup);
    render();
    $('demoPaymentDialog').close();
    demoOrder = null;
    $('notice').textContent =
      outcome === 'success'
        ? 'Demo payment succeeded. Balances updated. No real money moved.'
        : outcome === 'failed'
          ? 'Demo payment failed. Balances are unchanged.'
          : 'Demo payment cancelled. Balances are unchanged.';
    $('notice').style.color = '';
  } catch (error) {
    $('demoPaymentError').textContent = error.message;
  } finally {
    pending = false;
    buttons.forEach((b) => (b.disabled = false));
  }
}
$('settlements').onclick = (event) => {
  const button = event.target.closest('[data-demo-pay]');
  if (button) beginDemo(Number(button.dataset.demoPay));
  else razorpayCheckout(event);
};
$('demoPaymentDialog').onclick = (event) => {
  const button = event.target.closest('[data-demo-outcome]');
  if (button) finishDemo(button.dataset.demoOutcome);
};
$('closeDemoPayment').onclick = () => finishDemo('cancelled');
$('demoPaymentDialog').addEventListener('cancel', (event) => {
  event.preventDefault();
  finishDemo('cancelled');
});
$('paymentHistory').onclick = (event) => {
  const button = event.target.closest('[data-resume-demo]');
  if (!button) {
    reconcilePayment(event);
    return;
  }
  if (pending) return;
  const entry = state.payments.find((p) => p.orderId === button.dataset.resumeDemo);
  if (entry)
    openDemo(
      { orderId: entry.orderId, from: entry.from, to: entry.to, amount: entry.cents },
      currentGroup,
    );
};

async function refreshCsrf() {
  const response = await window.fetch('/api/auth/csrf', { cache: 'no-store' });
  csrf = await response.json();
}
async function secureFetch(url, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) await refreshCsrf();
    options = { ...options, headers: { ...options.headers, [csrf.headerName]: csrf.token } };
  }
  const response = await window.fetch(url, options);
  if (response.status === 401 && currentAccount) {
    showSignedOut();
    $('authError').textContent = 'Your session expired. Please sign in again.';
  }
  return response;
}
function showSignedOut() {
  $('groupsHome').hidden = true;
  $('groupPage').hidden = true;
  $('groupCards').innerHTML = '';
  routeSequence++;
  availableGroups = [];
  currentAccount = null;
  state = null;
  currentGroup = '';
  $('appMain').hidden = true;
  $('accountBar').hidden = true;
  $('authScreen').hidden = false;
  for (const d of document.querySelectorAll('dialog[open]')) d.close();
}
function authTab(mode) {
  authMode = mode;
  $('displayNameField').hidden = mode !== 'register';
  $('registerNote').hidden = mode !== 'register';
  $('authTitle').textContent = mode === 'register' ? 'Make room for your people.' : 'Welcome back.';
  $('authSubmit').textContent = mode === 'register' ? 'Create account ↗' : 'Sign in ↗';
  $('authPassword').autocomplete = mode === 'register' ? 'new-password' : 'current-password';
  $('loginTab').classList.toggle('dark', mode === 'login');
  $('registerTab').classList.toggle('dark', mode === 'register');
  $('authError').textContent = '';
}
$('loginTab').onclick = () => authTab('login');
$('registerTab').onclick = () => authTab('register');
async function signedIn() {
  const response = await secureFetch('/api/auth/me');
  if (!response.ok) throw Error('Please sign in.');
  currentAccount = await response.json();
  $('accountName').textContent = currentAccount.displayName + ' (@' + currentAccount.username + ')';
  $('authScreen').hidden = true;
  $('appMain').hidden = false;
  $('accountBar').hidden = false;
  await refreshCsrf();
  await initializeGroups();
}
$('authForm').onsubmit = async (event) => {
  event.preventDefault();
  const button = $('authSubmit');
  button.disabled = true;
  $('authError').textContent = '';
  try {
    const form = new URLSearchParams(new FormData(event.target));
    form.set('username', form.get('username').trim().toLowerCase());
    if (authMode === 'register') {
      const r = await secureFetch('/api/auth/register', { method: 'POST', body: form });
      const body = await r.json();
      if (!r.ok) throw Error(body.error || 'Could not create account.');
    }
    const response = await secureFetch('/api/auth/login', { method: 'POST', body: form });
    const result = await response.json();
    if (!response.ok) throw Error(result.error || 'Sign in failed.');
    event.target.reset();
    await signedIn();
  } catch (error) {
    $('authError').textContent = error.message;
  } finally {
    button.disabled = false;
  }
};
$('logout').onclick = async () => {
  if (pending) return;
  try {
    const response = await secureFetch('/api/auth/logout', { method: 'POST' });
    if (!response.ok) throw Error('Could not log out. Refresh and try again.');
    csrf = null;
    history.replaceState({}, '', '/');
    showSignedOut();
    await refreshCsrf();
  } catch (error) {
    feedback(error);
  }
};
async function loadInvitations() {
  const response = await secureFetch('/api/invitations');
  const list = await response.json();
  if (!response.ok) throw Error(list.error || 'Could not load invitations.');
  $('invitationPanel').hidden = list.length === 0;
  $('invitations').innerHTML =
    list
      .map(
        (i) =>
          `<div class="invitation"><span><b>${esc(i.groupName)}</b><small>Invited by @${esc(i.invitedBy)}</small></span><button class="green" data-invite="${esc(i.id)}" data-action="accept">Accept</button><button data-invite="${esc(i.id)}" data-action="decline">Decline</button></div>`,
      )
      .join('') ||
    '<p class="muted small">No pending invitations. Give your friends your username so they can add you.</p>';
}
$('invitations').onclick = async (event) => {
  const button = event.target.closest('[data-invite]');
  if (!button || pending) return;
  button.disabled = true;
  try {
    const response = await secureFetch(
      '/api/invitations/' + encodeURIComponent(button.dataset.invite) + '/' + button.dataset.action,
      { method: 'POST' },
    );
    const result = await response.json();
    if (!response.ok) throw Error(result.error || 'Could not answer invitation.');
    await initializeGroups();
    if (result.status === 'accepted')
      await navigate('/groups/' + encodeURIComponent(result.groupId));
    $('notice').textContent =
      result.status === 'accepted'
        ? 'Invitation accepted. Welcome to the group.'
        : 'Invitation declined.';
    $('notice').style.color = '';
  } catch (error) {
    feedback(error);
  } finally {
    button.disabled = false;
  }
};
$('refreshInvites').onclick = () => loadInvitations().catch(feedback);
$('refreshAccount').onclick = () => {
  if (!pending) initializeGroups().catch(feedback);
};
queueMicrotask(async () => {
  try {
    await refreshCsrf();
    const response = await secureFetch('/api/auth/me');
    if (response.ok) await signedIn();
    else showSignedOut();
  } catch (error) {
    $('authError').textContent = 'Could not connect to SplitSmart. Please refresh.';
  }
});

let selectedPerson = null,
  userSuggestions = [],
  activeSuggestion = -1,
  searchTimer,
  searchAbort,
  searchVersion = 0;
function closeUserSearch() {
  clearTimeout(searchTimer);
  searchAbort?.abort();
  searchVersion++;
  $('userResults').hidden = true;
  $('friend').setAttribute('aria-expanded', 'false');
  $('friend').removeAttribute('aria-activedescendant');
  activeSuggestion = -1;
}
function resetMemberSearch() {
  closeUserSearch();
  selectedPerson = null;
  userSuggestions = [];
  $('friend').value = '';
  $('addMemberButton').disabled = true;
}
function choosePerson(index) {
  const person = userSuggestions[index];
  if (!person) return;
  selectedPerson = person;
  closeUserSearch();
  $('friend').value = person.displayName + ' (@' + person.username + ')';
  $('addMemberButton').disabled = false;
  $('addMemberButton').focus();
}
function highlightPerson(index) {
  activeSuggestion = index;
  for (const [i, option] of [...$('userResults').querySelectorAll('[role="option"]')].entries())
    option.setAttribute('aria-selected', String(i === index));
  if (index >= 0) {
    $('friend').setAttribute('aria-activedescendant', 'person-option-' + index);
    $('userResults')
      .querySelector('#person-option-' + index)
      ?.scrollIntoView({ block: 'nearest' });
  }
}
async function searchPeople() {
  if (!currentGroup || !state?.isOwner) return;
  searchAbort?.abort();
  searchAbort = new AbortController();
  const version = ++searchVersion,
    group = currentGroup;
  userSuggestions = [];
  activeSuggestion = -1;
  $('userResults').hidden = false;
  $('friend').setAttribute('aria-expanded', 'true');
  $('userResults').innerHTML = '<div class="search-message">Searching…</div>';
  try {
    const response = await secureFetch(
      '/api/users?group=' +
        encodeURIComponent(group) +
        '&q=' +
        encodeURIComponent($('friend').value.trim()),
      { signal: searchAbort.signal },
    );
    const people = await response.json();
    if (version !== searchVersion || group !== currentGroup) return;
    if (!response.ok) throw Error(people.error || 'Could not search people.');
    userSuggestions = people;
    activeSuggestion = -1;
    $('userResults').innerHTML =
      people
        .map(
          (p, i) =>
            `<button type="button" tabindex="-1" role="option" aria-selected="false" id="person-option-${i}" data-person-index="${i}" class="person-option"><span class="avatar">${esc(p.displayName.slice(0, 2).toUpperCase())}</span><span><b>${esc(p.displayName)}</b><small>@${esc(p.username)}</small></span><span class="person-pick" aria-hidden="true">+</span></button>`,
        )
        .join('') ||
      '<div class="search-message">No available people found. Already-added members are excluded.</div>';
    $('userSearchStatus').textContent = people.length + ' people found.';
  } catch (error) {
    if (error.name === 'AbortError' || version !== searchVersion) return;
    $('userResults').innerHTML = '<div class="search-message">' + esc(error.message) + '</div>';
    $('userSearchStatus').textContent = error.message;
  }
}
$('friend').oninput = () => {
  selectedPerson = null;
  $('addMemberButton').disabled = true;
  closeUserSearch();
  searchTimer = setTimeout(searchPeople, 200);
};
$('friend').onfocus = () => {
  if (!selectedPerson) searchPeople();
};
$('friend').onkeydown = (event) => {
  if (event.key === 'Escape') {
    closeUserSearch();
    return;
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault();
    if ($('userResults').hidden) {
      searchPeople();
      return;
    }
    if (userSuggestions.length)
      highlightPerson(
        activeSuggestion < 0
          ? event.key === 'ArrowDown'
            ? 0
            : userSuggestions.length - 1
          : (activeSuggestion + (event.key === 'ArrowDown' ? 1 : -1) + userSuggestions.length) %
              userSuggestions.length,
      );
  }
  if (event.key === 'Enter' && !$('userResults').hidden) {
    event.preventDefault();
    if (activeSuggestion >= 0) choosePerson(activeSuggestion);
  }
};
$('userResults').onmousedown = (event) => event.preventDefault();
$('userResults').onclick = (event) => {
  const option = event.target.closest('[data-person-index]');
  if (option) choosePerson(Number(option.dataset.personIndex));
};
document.addEventListener('pointerdown', (event) => {
  if (!event.target.closest('#memberForm')) closeUserSearch();
});
