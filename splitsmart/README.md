# SplitSmart

A Spring Boot app for shared expenses, personal accounts, direct group membership, and transparent repayments.

## Project structure

Backend code is separated into `controller`, `service`, `repository`, `model`, `config`, `security`, `integration/payment`, `exception`, and `migration` packages. See [ARCHITECTURE.md](ARCHITECTURE.md) for the class map and request flow.

## Run locally

Requires JDK 17+. Maven is included through the wrapper. On macOS/Linux, run `./run.sh`. On Windows, run `mvnw.cmd package`, then `java -jar target/splitsmart-1.0.0.jar`.

Open http://localhost:8080 and choose **Create account**. Choose your own username and password. No default credentials or external registration are required. Registering also signs you in through the interface.

The default database is **persistent H2**, managed through Spring Data JPA and Flyway. PostgreSQL is configured as an optional profile; PostgreSQL is not installed or running in the current workspace.

## Group navigation

After sign-in, the home page lists all your groups as cards. Select a card to open `/groups/<id>`, with its members, expenses, splits, balances, and repayment history. **All groups** takes you home. Direct links, reloads, browser Back/Forward, and opening cards in a new tab work. Create groups on the home page; add people inside each group. Group API access still requires membership.

## Test with two people

1. Create your account in your usual browser.
2. Open the app in a different browser or a private browsing window and create a second account. Tabs in the same browser session share a login.
3. As the first account, create a group, focus **Search registered people**, and search by name or username. Select the second account from the dropdown, then click **Add member**.
4. As the second account, click **Refresh**. The group appears immediately; no request or acceptance is required.
5. Either member can add an expense and choose who paid and who shares it.
6. Log in as the person who owes money to use **Try demo payment**. Success updates balances; failure/cancellation leave them unchanged.

The owner can directly add registered users. The searchable dropdown excludes accounts already in the group and shows up to 20 matching names/usernames at a time. Search runs across all registered accounts. A newly added user gets group access immediately. Use **Refresh** to fetch changes made in another session.

## Permissions

- Only signed-in users can access group or payment APIs.
- Users see only groups they belong to.
- Only group owners add members, clear expenses, or load sample data.
- Members may add expenses, including expenses paid by another person.
- Only an expense's creator or the group owner may delete it.
- Only the linked payer account may create, finish, or verify a repayment.
- Only group owners can search for available registered people within a group.
- Legacy pending invitations remain answerable by their intended recipients; new additions do not create invitations.

Spring Security provides BCrypt password hashing, session authentication, CSRF protection, session ID rotation on login, HTTP-only session cookies, and logout invalidation. Password hashes never appear in API responses.

## Existing data migration

On the first start, a single database transaction imports `data/state.bin`, named groups under `data/groups/`, and their payment history. Original files remain unchanged as backups. The import is recorded in the database and does not run again on later starts.

**The first account you register receives the imported groups.** In an imported group, the legacy name `You` is linked to that account when available; otherwise its existing username or a new member entry is used. Other legacy names remain historical/guest entries, without fabricated accounts. New groups use real usernames and direct membership. Register your own first account before sharing the app.

Back up `data/` while the app is stopped. Current data now lives in `data/splitsmart.mv.db`, not in the old binary/JSON files. Database access uses transactions, foreign keys, unique memberships, versioning, and per-group write locks. Accounts, membership, invitations, and group metadata are relational; expenses and payment history are stored together as a JSON aggregate in each group's database row.

## PostgreSQL profile

Provide an existing PostgreSQL database and set environment variables:

- `SPRING_PROFILES_ACTIVE=postgres`
- `DATABASE_URL=jdbc:postgresql://localhost:5432/splitsmart`
- `DATABASE_USERNAME=splitsmart`
- `DATABASE_PASSWORD` to your database password

Then run `./run.sh`. Flyway applies the versioned schema; Hibernate validates it. The PostgreSQL driver and Flyway PostgreSQL adapter are bundled. This configuration has not been exercised against a running PostgreSQL server here. Switching profiles selects a separate database; it does not copy accounts/groups created in H2. Use a deliberate export/import for an existing database migration.

## Readable formatting

Java uses Spotless/Google Java Format, frontend files use Prettier, and Python tests use Black. Kiro-compatible format-on-save settings and extension recommendations are included. See [FORMATTING.md](FORMATTING.md) for IDE setup and repeatable commands.

## Build and test

```sh
./mvnw test
./mvnw package
python3 tests/api_test.py
```

JUnit checks financial rounding, 1,000 randomized balance cases, payment verification and idempotency, and three-user access controls using Spring Security and JPA. The Python suite starts the packaged app with an isolated file database and separate cookie sessions. It tests actual CSRF tokens, direct member additions, payer enforcement, legacy import, logout, and persistence after a server restart. It never uses your database or credentials.

## Payments

The local simulator needs no payment-provider registration or keys. Every simulated outcome is labelled as a demo. No real money moves. Optional Razorpay sandbox integration remains available when server test keys are configured. See [PAYMENTS.md](PAYMENTS.md).

## Calculation rules

All money is integer paise. Equal splits allocate leftover paise in participant order. Percentage splits use largest remainders with deterministic ties; exact shares must sum to the bill. Net balance = amount paid − own share + successful repayments sent − successful repayments received. Greedy matching clears all balances in at most n−1 transfers, without guaranteeing the globally fewest transfers.

## Current scope

Local multi-user MVP, bound to loopback. H2 runs without installation. No email delivery, password recovery, email verification, live payments, refunds, or automatic payment webhooks. Session logins expire after 30 minutes and end on server restart. Group state includes all expenses, with progressive display in the interface. Future work includes normalized expense tables, deployment and HTTPS, and production authentication controls.

## Git and private data

Commit from this project folder. The entire local `data/` directory, secrets, generated packages, and logs are ignored; your local data remains in place. See [GIT.md](GIT.md) for the pre-commit checks and backup guidance.
