# SplitSmart architecture

The backend is organized under `src/main/java/com/splitsmart/`:

```text
com/splitsmart/
├── SplitSmartApplication.java       # Spring Boot entry point
├── controller/                     # HTTP routes and request/response handling
│   ├── AuthController.java
│   ├── ExpenseController.java
│   ├── PaymentController.java
│   └── PageController.java
├── service/                        # Business rules and transactional operations
│   ├── AccountService.java
│   ├── ExpenseService.java         # Pure allocation and balance calculations
│   ├── GroupService.java           # Membership, expenses, permissions, group transactions
│   └── PaymentService.java         # Payment simulation and provider verification
├── repository/                     # Spring Data JPA access and locking queries
│   ├── AccountRepository.java
│   ├── GroupRepository.java
│   ├── MembershipRepository.java
│   ├── InvitationRepository.java
│   └── SystemStateRepository.java
├── model/                          # Persisted entities and financial value objects
│   ├── Account.java
│   ├── GroupEntity.java
│   ├── Membership.java
│   ├── Invitation.java
│   ├── SystemState.java
│   ├── GroupState.java
│   ├── Expense.java
│   └── PaymentEntry.java
├── config/
│   └── WebConfiguration.java       # Static asset configuration
├── security/
│   ├── SecurityConfiguration.java  # Session authentication, CSRF, password encoder
│   ├── DatabaseUserDetailsService.java
│   └── HmacSignature.java          # Constant-time signature comparison
├── integration/payment/
│   ├── GatewayClient.java          # External provider boundary
│   └── RazorpayClient.java         # Test-only Razorpay HTTP implementation
├── exception/
│   ├── ApiException.java           # Safe message and HTTP status
│   └── GlobalExceptionHandler.java # JSON error translation
└── migration/
    └── LegacyImport.java           # One-time import from old local files
```

## Request flow

The browser calls a controller through Spring Security. Controllers obtain the authenticated account and pass request data to services. Services enforce group ownership/membership and payer permissions, apply business rules, and use repositories inside database transactions. Repositories map model entities to H2 or PostgreSQL.

PaymentService uses GatewayClient for optional provider operations. RazorpayClient owns external HTTP calls. Provider integration code does not depend on controllers or repositories. Expense and payment records live in `model`, rather than being nested inside services. JPA entity fields are private and accessed through explicit methods; the password hash getter is excluded from JSON serialization.

## Other source folders

- `src/main/resources/static/`: HTML, CSS, and browser JavaScript.
- `src/main/resources/db/migration/`: versioned Flyway SQL schema.
- `src/main/resources/application*.properties`: shared configuration and PostgreSQL profile.
- `src/test/java/com/splitsmart/service/`: financial and payment unit tests.
- `src/test/java/com/splitsmart/integration/`: multi-user Spring Security/JPA integration tests.
- `tests/api_test.py`: real HTTP tests with an isolated database and sessions.

## Persistence boundaries

Accounts, memberships, invitations, and group metadata use relational tables. A group's expenses and payment history remain a JSON aggregate in the group row, committed under a write lock. This package refactor does not normalize that aggregate into separate expense/payment tables. Existing Flyway migrations, table names, stored JSON keys, routes, and response shapes remain compatible.

Run `./mvnw test`, then `./mvnw package` and `python3 tests/api_test.py` to verify the layers together.
