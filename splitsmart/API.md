# SplitSmart API

Base URL: http://localhost:8080. Spring Boot 3.5.16, Spring Security, Spring Data JPA, Flyway. JSON responses, form-encoded POST bodies. Money in responses is integer paise; form amounts use rupees.

## Authentication

1. GET `/api/auth/csrf` to obtain `{token, headerName}` and a session cookie.
2. Keep cookies and send the token in the returned header on every POST, including registration, login, and logout.
3. POST `/api/auth/register`: `username`, `displayName` (optional), `password`. Returns 201; username is case-normalized and unique, 3–30 letters/digits/underscores; password at least 8 characters, at most 72 UTF-8 bytes.
4. POST `/api/auth/login`: `username`, `password`. Returns 200 on success or 401 on invalid credentials. Refresh the CSRF token after login, because Spring Security rotates the session/token.
5. GET `/api/auth/me`: returns only your username and display name.
6. POST `/api/auth/logout`: invalidates the session. Fetch a new CSRF token before logging in again.

Accounts and password hashes persist; sessions do not survive an application restart. Group APIs require authentication (401 when signed out). Invalid/missing CSRF tokens are rejected with 403.

## Groups and membership

| Method | Endpoint                    | Fields / result                                                                                                  |
| ------ | --------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| GET    | /api/groups                 | Only the current user's groups                                                                                   |
| POST   | /api/groups                 | `name`; creates a group containing its owner; returns 201 and selected ID                                        |
| GET    | /api/state?group=ID         | Members, expenses, balances, transfers, payments, viewerMember, isOwner, registeredMembers, canDelete            |
| GET    | /api/users?group=ID&q=QUERY | Owner-only name/username search; up to 20 results; existing members excluded; only username/displayName returned |
| POST   | /api/members?group=ID       | `username`; owner adds an existing account immediately                                                           |
| GET    | /api/invitations            | Pending invitations addressed to the current user                                                                |
| POST   | /api/invitations/ID/accept  | Recipient joins the group atomically                                                                             |
| POST   | /api/invitations/ID/decline | Recipient declines                                                                                               |

Knowing a group ID does not grant access. Only group owners can search and add users. Adding a member commits membership and the group participant entry together; no request or acceptance is needed. Duplicate membership returns 409. Legacy invitation endpoints remain for existing pending requests.

## Expenses

POST `/api/expenses?group=ID`: `title`, `amount`, `payer`, repeated `member`, `mode` (`equal`, `exact`, `percentage`), repeated `share` for uneven splits in matching participant order. A member can record an expense paid by any member listed in the group. Created-by account is tracked.

POST `/api/delete?group=ID`: `id`. Requires expense creator or group owner.

POST `/api/reset?group=ID` and `/api/demo?group=ID`: owner only. They preserve registered membership; groups with payment history cannot be replaced. Demo adds clearly identifiable sample guests, without creating login accounts.

## Payment simulations and optional Razorpay

All payment operations require current group membership. The authenticated account's linked member name must match the payer; this is checked on order creation, simulation completion, and gateway verification/reconciliation.

- GET `/api/payments/config`
- POST `/api/payments/demo-orders?group=ID`: `from`, `to`. Amount calculated on server; returns 201.
- POST `/api/payments/demo-complete?group=ID`: `orderId`, `outcome=success|failed|cancelled`.
- POST `/api/payments/orders?group=ID`: `from`, `to`. Optional Razorpay test order.
- POST `/api/payments/verify?group=ID`: `razorpay_order_id`, `razorpay_payment_id`, `razorpay_signature`.
- POST `/api/payments/reconcile?group=ID`: `orderId`.

Only successful simulations or provider-confirmed captured test payments affect balances. Replays are idempotent. See [PAYMENTS.md](PAYMENTS.md).

## Errors and limits

400 invalid input; 401 authentication needed; 403 forbidden group/action or CSRF; 404 missing record; 409 conflict/stale balance; 415 wrong content type; 503 optional Razorpay unavailable. Standard Spring error responses may apply to unsupported routes/methods. Passwords/hashes are never returned. Group state is protected by database transactions and write locks; no shared active-group global exists.

Limits: 100 groups per account, 30 participants per group, 10,000 expenses/payment records, amount ₹0.01–₹1,000,000. Razorpay checkout minimum ₹1. Local simulator supports paise. Default embedded database is persistent H2; optional PostgreSQL profile uses the same Flyway schema.

## Browser pages

`/` is the groups home page after sign-in. `/groups/<id>` serves the application shell for an individual workspace, including on direct navigation/reload. Unauthenticated visitors see sign-in; membership is enforced by the data APIs, not by possession of the URL.
