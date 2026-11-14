# Demo payments and optional Razorpay test payments

## No-registration simulator (available by default)

Choose **Try demo payment** on a suggested repayment. The local checkout offers **Simulate success**, **Simulate failure**, and **Cancel payment**. No payment-provider registration, API keys, payment details, or provider connection is needed. A SplitSmart account and group membership are required; only the logged-in payer can simulate their own repayment. Amounts come from server balances. Successful simulations update balances; failed/cancelled attempts only update history. All attempts are labelled as demos and persist across restarts. Repeated submissions cannot apply a repayment twice. Pending attempts can be resumed from history. If debts change while a demo is open, stale successful completion is rejected; cancel it and start another attempt.

POST `/api/payments/demo-orders?group=<id>` with `from`, `to` creates a local attempt. POST `/api/payments/demo-complete?group=<id>` with `orderId` and `outcome` (`success`, `failed`, `cancelled`) records the chosen outcome and returns updated state. Demo orders start with `demo_`, distinct from Razorpay orders. They do not claim provider verification. Demo balances include successful simulations, so use a separate group for demonstrations you do not want mixed with personal expenses.

## Optional Razorpay sandbox

SplitSmart also integrates Razorpay Standard Checkout in **test mode only**. This is a portfolio sandbox: no real money moves and friends do not receive merchant payouts. Live-mode keys are deliberately rejected.

## Enable checkout

1. In your own Razorpay account, select Test mode and generate test API keys.
2. Set `RAZORPAY_KEY_ID` (starts with `rzp_test_`) and `RAZORPAY_KEY_SECRET` in your terminal environment or IDE run configuration.
3. Stop the running app and restart with `./run.sh`, or run the packaged JAR from the project directory. Spring Boot reads the environment at startup.
4. Configure automatic capture in the Razorpay dashboard for testing. Open a group with an outstanding balance and select **Pay in test mode**.
5. Complete checkout using Razorpay's documented test payment options. Only a captured payment verified by the backend changes demo balances.

Do not paste the secret into chat, JavaScript, source files, or Git. No keys are included in this project. The public key ID is returned to Checkout only when configured; the key secret stays on the server.

## Flow

- The browser selects the payer and recipient from a suggested repayment.
- Spring Boot calculates the amount from the group's current net balances, ignoring any client-supplied amount.
- The server creates an INR test order with Razorpay and saves its parties and amount atomically.
- Razorpay's hosted Checkout handles payment details.
- The callback sends order ID, payment ID, and signature to the backend.
- The backend validates HMAC-SHA256 using the stored order ID, then fetches the payment from Razorpay. The payment ID, order ID, amount, currency, and captured status must match.
- The transactional database ledger records verified test repayments exactly once. Payer balance increases and recipient balance decreases by the payment amount. Expense totals are unchanged.

Repeated order requests for the same unverified repayment reuse the saved order. Replayed verification callbacks are idempotent. Orders are scoped to their group. Group expenses and payment history commit together in a database transaction. Legacy files are preserved by the one-time import.

## Closed checkout, delayed capture, or lost callback

An unverified order appears as **Awaiting verification**. This does not mean a payment succeeded or failed. Use **Check payment status** before paying again. The server fetches the order's captured payment directly from Razorpay and validates it before recording it; this works after a restart or when a browser callback is lost.

No automatic webhooks are implemented in this version. A payment made without a successful callback needs the status check. Pending orders do not reserve a balance, so avoid concurrent sandbox checkouts for the same debts. There is no refund workflow. Groups with payment records cannot be cleared or replaced by demo data; create another group to start fresh.

## Storage and API

Payment history now lives in the group database aggregate, committed atomically with balance changes. Legacy JSON files are imported once and preserved. Back up the stopped H2 database in `data/splitsmart.mv.db`, or your PostgreSQL database. Every payment API requires a session, CSRF header, membership, and the logged-in payer.

GET `/api/payments/config` returns enabled/provider/mode information, never the secret.

POST bodies use `application/x-www-form-urlencoded` and may include `?group=<id>`:

| Endpoint                  | Fields                                                           | Result                                                                                          |
| ------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `/api/payments/orders`    | `from`, `to`                                                     | 201 with public key ID, provider order ID, server-calculated amount, INR currency and test mode |
| `/api/payments/verify`    | `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature` | Updated group state after verification                                                          |
| `/api/payments/reconcile` | `orderId`                                                        | Updated state after provider-confirmed capture                                                  |

Checkout accepts repayments between ₹1 and ₹1,000,000. Smaller balances remain displayed for manual settlement. Missing or live keys return 503. Invalid signatures/details return 400, unknown orders 404, uncaptured payments 409, provider connection errors 502, and persistence errors 500.

## Verification performed

JUnit uses a fake provider boundary to check successful capture, duplicate callbacks, server-calculated amounts, restart persistence, signature rejection, mismatched amounts/currency, pending capture, cross-group rejection, and reconciliation. An RFC HMAC-SHA256 test vector checks the real signature implementation. The HTTP suite checks that missing keys disable checkout and that requests enforce origin and content type. These tests do not contact Razorpay.

A real Razorpay sandbox checkout still requires your test account credentials and must be checked after configuration. This local app uses account authentication and payer permissions, but is not ready for public real-payment use. A real peer-to-peer product would also need an approved recipient/payout design rather than ordinary merchant checkout.

## Official references

- [Standard Checkout integration](https://razorpay.com/docs/payments/payment-gateway/web-integration/standard/integration-steps/)
- [Java integration and payment verification](https://razorpay.com/docs/payments/server-integration/java/integration-steps/)
