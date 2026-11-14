package com.splitsmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.exception.ApiException;
import com.splitsmart.model.Expense;
import com.splitsmart.model.GroupEntity;
import com.splitsmart.model.GroupState;
import com.splitsmart.model.Membership;
import com.splitsmart.repository.AccountRepository;
import com.splitsmart.repository.GroupRepository;
import com.splitsmart.repository.InvitationRepository;
import com.splitsmart.repository.MembershipRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entities;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final InvitationRepository invitations;
    private final AccountRepository accounts;
    private final PaymentService payments;
    private final ObjectMapper json;

    public GroupService(
            GroupRepository groups,
            MembershipRepository memberships,
            InvitationRepository invitations,
            AccountRepository accounts,
            PaymentService payments,
            ObjectMapper json) {
        this.groups = groups;
        this.memberships = memberships;
        this.invitations = invitations;
        this.accounts = accounts;
        this.payments = payments;
        this.json = json;
    }

    private static ApiException error(int status, String message) {
        return new ApiException(status, message);
    }

    static String value(Map<String, List<String>> form, String key) {
        return form.getOrDefault(key, List.of("")).get(0).trim();
    }

    private String title(String name) {
        if (name.isBlank() || name.length() > 80 || name.chars().anyMatch(Character::isISOControl))
            throw error(400, "Use 1–80 printable characters.");
        return name;
    }

    private GroupEntity group(String id, String user, boolean lock) {
        if (id == null || id.isBlank()) throw error(400, "Select a group first.");
        if (memberships.findByGroupIdAndUsername(id, user).isEmpty())
            throw error(403, "You are not a member of this group.");
        return (lock ? groups.lockById(id) : groups.findById(id))
                .orElseThrow(() -> error(404, "Group not found."));
    }

    private GroupState read(GroupEntity group) {
        try {
            return json.readValue(group.getPayload(), GroupState.class);
        } catch (Exception e) {
            throw error(500, "Could not read group data.");
        }
    }

    private void save(GroupEntity group, GroupState state) {
        try {
            group.setPayload(json.writeValueAsString(state));
            groups.saveAndFlush(group);
        } catch (java.io.IOException e) {
            throw error(500, "Could not save group data.");
        }
    }

    private void owner(GroupEntity group, String user) {
        if (!user.equals(group.getOwnerUsername()))
            throw error(403, "Only the group owner can do this.");
    }

    private Membership member(String group, String username) {
        return memberships
                .findByGroupIdAndUsername(group, username)
                .orElseThrow(() -> error(403, "You are not a group member."));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(String user) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (var member : memberships.findByUsername(user))
            groups.findById(member.getGroupId())
                    .ifPresent(
                            g ->
                                    result.add(
                                            Map.of(
                                                    "id",
                                                    g.getId(),
                                                    "name",
                                                    g.getName(),
                                                    "isOwner",
                                                    user.equals(g.getOwnerUsername()))));
        result.sort(Comparator.comparing(g -> g.get("name").toString()));
        return Map.of(
                "groups", result, "selected", result.isEmpty() ? "" : result.get(0).get("id"));
    }

    public Map<String, Object> create(String user, String name) {
        name = title(name);
        if (memberships.findByUsername(user).size() >= 100)
            throw error(400, "Maximum 100 groups per account.");
        GroupState state = new GroupState();
        state.members.add(user);
        String id = UUID.randomUUID().toString();
        var group = new GroupEntity(id, name, user, "{}");
        save(group, state);
        memberships.saveAndFlush(new Membership(id, user, user));
        var result = new LinkedHashMap<>(list(user));
        result.put("selected", id);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> state(String user, String id) {
        var group = group(id, user, false);
        return view(group, read(group), user);
    }

    private long[] balances(GroupState state) {
        long[] b = ExpenseService.balances(state.members, state.expenses);
        for (var payment : state.payments)
            if (PaymentService.countsAsRepayment(payment)) {
                int from = state.members.indexOf(payment.from()),
                        to = state.members.indexOf(payment.to());
                if (from < 0 || to < 0) throw error(500, "Payment history has an unknown member.");
                b[from] += payment.cents();
                b[to] -= payment.cents();
            }
        return b;
    }

    private Map<String, Object> view(GroupEntity group, GroupState state, String user) {
        long[] b = balances(state), remaining = b.clone();
        List<Map<String, Object>> transfers = new ArrayList<>(), totals = new ArrayList<>();
        while (true) {
            int debtor = -1, creditor = -1;
            for (int i = 0; i < b.length; i++) {
                if (remaining[i] < 0 && (debtor < 0 || remaining[i] < remaining[debtor]))
                    debtor = i;
                if (remaining[i] > 0 && (creditor < 0 || remaining[i] > remaining[creditor]))
                    creditor = i;
            }
            if (debtor < 0 || creditor < 0) break;
            long amount = Math.min(-remaining[debtor], remaining[creditor]);
            remaining[debtor] += amount;
            remaining[creditor] -= amount;
            transfers.add(
                    Map.of(
                            "from",
                            state.members.get(debtor),
                            "to",
                            state.members.get(creditor),
                            "cents",
                            amount));
        }
        for (int i = 0; i < b.length; i++)
            totals.add(Map.of("name", state.members.get(i), "cents", b[i]));
        List<Map<String, String>> registered = new ArrayList<>();
        for (var m : memberships.findByGroupId(group.getId()))
            registered.add(Map.of("username", m.getUsername(), "memberName", m.getMemberName()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("members", state.members);
        result.put("expenses", state.expenses);
        result.put("payments", state.payments);
        result.put("balances", totals);
        result.put("transfers", transfers);
        result.put("paymentConfig", payments.config());
        result.put("viewerMember", member(group.getId(), user).getMemberName());
        result.put("isOwner", user.equals(group.getOwnerUsername()));
        result.put("registeredMembers", registered);
        result.put(
                "canDelete",
                state.expenses.stream()
                        .filter(
                                e ->
                                        user.equals(group.getOwnerUsername())
                                                || user.equals(e.createdBy()))
                        .map(Expense::id)
                        .toList());
        return result;
    }

    public Map<String, Object> mutate(
            String user, String id, String operation, Map<String, List<String>> form) {
        var group = group(id, user, true);
        var state = read(group);
        switch (operation) {
            case "members" -> {
                addMember(group, state, user, value(form, "username"));
                save(group, state);
                return view(group, state, user);
            }
            case "expenses" -> {
                String description = title(value(form, "title")),
                        payer = value(form, "payer"),
                        mode = value(form, "mode");
                long cents;
                try {
                    cents =
                            new BigDecimal(value(form, "amount"))
                                    .movePointRight(2)
                                    .longValueExact();
                } catch (Exception e) {
                    throw error(400, "Enter an amount with at most two decimals.");
                }
                if (cents <= 0 || cents > 100000000)
                    throw error(400, "Amount must be between 0.01 and 1,000,000.");
                var selected = form.getOrDefault("member", List.of());
                if (!state.members.contains(payer)
                        || selected.isEmpty()
                        || !state.members.containsAll(selected)
                        || new HashSet<>(selected).size() != selected.size())
                    throw error(400, "Choose a payer and valid group members.");
                if (state.expenses.size() >= 10000) throw error(400, "Expense limit reached.");
                if (mode.isEmpty()) mode = "equal";
                var values = form.getOrDefault("share", Collections.nCopies(selected.size(), "0"));
                if (values.size() != selected.size())
                    throw error(400, "Provide one share for each participant.");
                state.expenses.add(
                        new Expense(
                                UUID.randomUUID().toString(),
                                description,
                                cents,
                                payer,
                                List.copyOf(selected),
                                ExpenseService.allocate(cents, mode, values),
                                mode,
                                user));
            }
            case "delete" -> {
                var expense =
                        state.expenses.stream()
                                .filter(e -> e.id().equals(value(form, "id")))
                                .findFirst()
                                .orElseThrow(() -> error(404, "Expense not found."));
                if (!user.equals(group.getOwnerUsername()) && !user.equals(expense.createdBy()))
                    throw error(403, "Only the expense creator or group owner can delete it.");
                state.expenses.remove(expense);
            }
            case "reset", "demo" -> {
                owner(group, user);
                if (!state.payments.isEmpty())
                    throw error(
                            409,
                            "This group has payment history. Create a new group to start fresh.");
                state.expenses.clear();
                state.members =
                        new ArrayList<>(
                                memberships.findByGroupId(id).stream()
                                        .map(m -> m.getMemberName())
                                        .toList());
                if (operation.equals("demo")) {
                    for (String name : List.of("Maya", "Arjun", "Zoe"))
                        if (!state.members.contains(name) && state.members.size() < 30)
                            state.members.add(name);
                    String payer = member(id, user).getMemberName();
                    state.expenses.add(
                            new Expense(
                                    UUID.randomUUID().toString(),
                                    "Seaside stay",
                                    180000,
                                    payer,
                                    List.copyOf(state.members),
                                    ExpenseService.allocate(
                                            180000,
                                            "equal",
                                            Collections.nCopies(state.members.size(), "0")),
                                    "equal",
                                    user));
                }
            }
            default -> throw error(404, "Unknown operation.");
        }
        save(group, state);
        return view(group, state, user);
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> searchUsers(String user, String id, String query) {
        var group = group(id, user, false);
        owner(group, user);
        query = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (query.length() > 80) throw error(400, "Search must be at most 80 characters.");
        return accounts
                .searchAvailable(id, query, org.springframework.data.domain.PageRequest.of(0, 20))
                .stream()
                .map(a -> Map.of("username", a.getUsername(), "displayName", a.getDisplayName()))
                .toList();
    }

    private void addMember(GroupEntity group, GroupState state, String user, String input) {
        owner(group, user);
        String target = AccountService.username(input);
        if (!accounts.existsById(target)) throw error(404, "That user is not registered.");
        if (memberships.findByGroupIdAndUsername(group.getId(), target).isPresent())
            throw error(409, "This user is already a member.");
        if (!state.members.contains(target)) {
            if (state.members.size() >= 30) throw error(400, "Group is full.");
            state.members.add(target);
        }
        if (memberships.findByUsername(target).size() >= 100)
            throw error(400, "This user has reached the group limit.");
        memberships.saveAndFlush(new Membership(group.getId(), target, target));
        for (var invitation : invitations.findByTargetUsernameAndStatus(target, "pending"))
            if (invitation.getGroupId().equals(group.getId())) {
                invitation.setStatus("accepted");
                invitations.save(invitation);
            }
    }

    @Transactional(readOnly = true)
    public List<Map<String, String>> invitations(String user) {
        return invitations.findByTargetUsernameAndStatus(user, "pending").stream()
                .map(
                        i ->
                                Map.of(
                                        "id",
                                        i.getId(),
                                        "groupName",
                                        groups.findById(i.getGroupId()).orElseThrow().getName(),
                                        "invitedBy",
                                        i.getInvitedBy()))
                .toList();
    }

    public Map<String, String> answerInvitation(String user, String id, boolean accept) {
        var invitation =
                invitations.findById(id).orElseThrow(() -> error(404, "Invitation not found."));
        if (!invitation.getTargetUsername().equals(user))
            throw error(403, "This invitation belongs to another user.");
        var group = groups.lockById(invitation.getGroupId()).orElseThrow();
        entities.refresh(invitation, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!invitation.getStatus().equals("pending"))
            throw error(409, "Invitation already answered.");
        if (accept) {
            var state = read(group);
            if (memberships.findByGroupIdAndUsername(group.getId(), user).isPresent())
                throw error(409, "Already a member.");
            if (!state.members.contains(user)) {
                if (state.members.size() >= 30) throw error(400, "Group is full.");
                state.members.add(user);
            }
            memberships.saveAndFlush(new Membership(group.getId(), user, user));
            save(group, state);
            invitation.setStatus("accepted");
        } else invitation.setStatus("declined");
        invitations.save(invitation);
        return Map.of("groupId", group.getId(), "status", invitation.getStatus());
    }

    public Map<String, Object> payment(
            String user, String id, String operation, Map<String, List<String>> form) {
        var group = group(id, user, true);
        var state = read(group);
        String me = member(id, user).getMemberName();
        if (operation.equals("orders") || operation.equals("demo-orders")) {
            String from = value(form, "from"), to = value(form, "to");
            if (!me.equals(from)) throw error(403, "You can only pay as yourself.");
            int fi = state.members.indexOf(from), ti = state.members.indexOf(to);
            if (fi < 0 || ti < 0 || fi == ti) throw error(400, "Choose different group members.");
            long[] b = balances(state);
            if (b[fi] >= 0 || b[ti] <= 0) throw error(409, "Balances changed. Refresh first.");
            long amount = Math.min(-b[fi], b[ti]);
            var result =
                    operation.equals("demo-orders")
                            ? payments.createDemo(state.payments, from, to, amount)
                            : payments.create(state.payments, from, to, amount);
            save(group, state);
            return result;
        }
        String orderId = value(form, operation.equals("verify") ? "razorpay_order_id" : "orderId");
        var entry =
                state.payments.stream()
                        .filter(p -> p.orderId().equals(orderId))
                        .findFirst()
                        .orElseThrow(() -> error(404, "Payment not found in this group."));
        if (!me.equals(entry.from()))
            throw error(403, "Only the payer can complete or verify this payment.");
        switch (operation) {
            case "demo-complete" -> {
                String outcome = value(form, "outcome");
                if (entry.status().equals("demo_created") && outcome.equals("success")) {
                    long[] b = balances(state);
                    int from = state.members.indexOf(entry.from()),
                            to = state.members.indexOf(entry.to());
                    if (b[from] >= 0 || b[to] <= 0 || entry.cents() > Math.min(-b[from], b[to]))
                        throw error(
                                409, "Balances changed. Cancel this attempt and start another.");
                }
                payments.completeDemo(state.payments, orderId, outcome);
            }
            case "verify" ->
                    payments.verify(
                            state.payments,
                            orderId,
                            value(form, "razorpay_payment_id"),
                            value(form, "razorpay_signature"));
            case "reconcile" -> payments.reconcile(state.payments, orderId);
            default -> throw error(404, "Unknown payment operation.");
        }
        save(group, state);
        return view(group, state, user);
    }
}
