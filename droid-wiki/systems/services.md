# Services

Active contributors: ajpahl1008 (AJ Pahl)

Two Spring services hold all of the application's business logic: `AccountService` and `TransactionService`. Views call them directly, and they in turn are the only callers of the account and transaction repositories during a user request.

## Purpose

- Resolve who is signed in and which `AccountHolder` that is.
- Read accounts, balances and transactions.
- Hand out full account numbers only to their owner, and record each reveal.
- Put every repository call behind a circuit breaker so a failing database fails fast.

## Directory layout

```
src/main/java/com/mockingbirdbank/service/
  AccountService.java       "accounts" breaker, holder resolution, reveal
  TransactionService.java   "transactions" breaker, history
src/test/java/com/mockingbirdbank/service/
  AccountServiceTest.java                      Mockito unit tests
  TransactionServiceTest.java                  Mockito unit test
  AccountTransactionQueryPerformanceTest.java  Testcontainers N+1 guard
```

The package has 2 main files (172 lines) and 3 test files (352 lines), about two test lines per main line.

## Key abstractions

| Method | File | Repository call | Returns or throws | Analytics |
| --- | --- | --- | --- | --- |
| `AccountService.currentHolder()` | `src/main/java/com/mockingbirdbank/service/AccountService.java` | `AppUserRepository.findByUsername` | The linked `AccountHolder`; `IllegalStateException` if the user has no holder | none |
| `AccountService.accountsFor(holder)` | same | `AccountRepository.findByHolderIdOrderByAccountTypeAscIdAsc` | Accounts, CHECKING before SAVINGS, then by ID | none |
| `AccountService.requireAccount(id)` | same | `AccountRepository.findById` | The `Account`; `IllegalArgumentException` if missing | none (the caller tracks `not_found`) |
| `AccountService.revealAccountNumber(id)` | same | `currentHolder()` then `findById` | Full number; `AccessDeniedException` if missing or owned by someone else | `account_number.revealed` with `scope=single`, or `trackError(..., "forbidden", ...)` |
| `AccountService.revealAccountNumbers()` | same | `currentHolder()` then `accountsFor` | `Map<Long, String>` of account ID to full number, in list order | `account_number.revealed` with `scope=all` and `accountCount` |
| `AccountService.totalBalance(holder)` | same | `accountsFor(holder)` | Sum of balances, `BigDecimal.ZERO` if none | none |
| `TransactionService.forAccount(id)` | `src/main/java/com/mockingbirdbank/service/TransactionService.java` | `TransactionRepository.findByAccountIdOrderByPostedAtDesc` | Transactions, newest first | none |

Any method can also throw `ServiceUnavailableException` (from `src/main/java/com/mockingbirdbank/resilience/ServiceUnavailableException.java`) when its breaker is open.

## How it works

### Resolving the current holder

`currentHolder()` reads `SecurityContextHolder.getContext().getAuthentication().getName()`, looks up the `AppUser` with that username, and returns `appUser.getHolder()`. There is no caching: every call is a database query. A dashboard load calls it twice, once from `MainLayout` for the greeting and once from `DashboardView`. Since `AppUser.holder` is a `@OneToOne` (eager by default), the holder comes back in the same query.

The username comes from `AppUserDetailsService` at login (see [Sign-in](../features/sign-in.md)). The login credentials and the holder's personal data are separate tables linked one-to-one; see [Persistence](persistence.md).

### The protectedCall pattern

Each service has a private `protectedCall(Supplier<T>)`:

```java
private <T> T protectedCall(Supplier<T> repositoryCall) {
    try {
        return circuitBreaker.executeSupplier(repositoryCall);
    } catch (CallNotPermittedException e) {
        throw new ServiceUnavailableException("Accounts are temporarily unavailable", e);
    }
}
```

The breakers are fetched by name from the shared `CircuitBreakerRegistry` in each constructor: `"accounts"` for `AccountService`, `"transactions"` for `TransactionService`. The two are independent, so a run of failing transaction queries does not stop the dashboard from loading.

Only the repository call goes inside the supplier. `orElseThrow` and the ownership filter run outside it, so "no such account" or "not your account" never count as breaker failures. A comment on `AccountService.protectedCall` states this rule. A real database exception still propagates unchanged; it just counts toward the breaker's failure rate. Thresholds and state transitions are on the [Resilience](resilience.md) page.

```mermaid
sequenceDiagram
    participant V as AccountNumberDisplay
    participant S as AccountService
    participant CB as accounts breaker
    participant R as AccountRepository
    participant A as AnalyticsEventService
    V->>S: revealAccountNumber id
    S->>CB: currentHolder lookup
    CB->>R: findByUsername
    S->>CB: protectedCall findById
    CB->>R: findById
    R-->>S: Optional Account
    alt owned by current holder
        S->>A: track account_number.revealed scope=single
        S-->>V: full number
    else missing or foreign
        S->>A: trackError account_number.revealed forbidden
        S-->>V: AccessDeniedException
    end
```

### Exception semantics

| Exception | Meaning | Thrown by | How the UI handles it |
| --- | --- | --- | --- |
| `IllegalStateException` | Signed-in user has no linked holder (data problem) | `currentHolder()` | Not caught; reaches `GlobalErrorHandler` |
| `IllegalArgumentException` | Account ID does not exist | `requireAccount()` | `AccountDetailView` tracks `account.viewed` / `not_found` and throws Vaadin's `NotFoundException` |
| `AccessDeniedException` (Spring Security) | Account missing or not owned, on the reveal path | `revealAccountNumber()` | `AccountNumberDisplay` shows a notification |
| `ServiceUnavailableException` | Breaker is open | any method | `AccountNumberDisplay` and `DashboardView.toggleAll()` show a notification; elsewhere it reaches `GlobalErrorHandler` |

The reveal path deliberately returns the same `AccessDeniedException` for a missing ID and for someone else's ID, so a caller cannot probe which account IDs exist. `AccountServiceTest` has one test for each case (`revealAccountNumberDeniesAccountOwnedBySomeoneElse`, `revealAccountNumberDeniesUnknownAccountTheSameWay`).

`requireAccount()` and `TransactionService.forAccount()` do not check ownership. `AccountDetailView` passes the URL's account ID straight to both, so any signed-in user who types `/accounts/{id}` for another holder's account would see its masked header, balance and transactions. With a single seeded holder this cannot happen today, but it would matter as soon as a second holder exists. See [Account detail](../features/account-detail.md) and [Security](../security.md).

### Read-only transactions

Both classes carry `@Transactional(readOnly = true)` at class level, so each public method runs in a read-only transaction and Hibernate skips dirty checking. `spring.jpa.open-in-view` is `false` in `src/main/resources/application.yml`, so the persistence context closes when the service method returns. Views therefore must not touch lazy collections (`Account.transactions`, `AccountHolder.accounts`) on entities they receive; transactions are fetched through `TransactionService.forAccount()` instead.

Internal calls such as `revealAccountNumbers()` calling `accountsFor()` are plain Java self-invocations and run inside the caller's transaction.

## Integration points

- **Callers:** `MainLayout`, `DashboardView`, `AccountDetailView`, `AccountNumberDisplay` (see [UI](ui.md)).
- **Repositories:** `AppUserRepository`, `AccountRepository`, `TransactionRepository` (see [Persistence](persistence.md)).
- **Circuit breakers:** `CircuitBreakerRegistry` bean from `ResilienceConfig` (see [Resilience](resilience.md)).
- **Analytics:** only `AccountService` injects `AnalyticsEventService`, and only for the reveal events. Page-view events are emitted by the views (see [Analytics](analytics.md)).

## Entry points for modification

- New read: add a derived query to the repository, call it through `protectedCall`, and keep any `orElseThrow` or filtering outside the supplier.
- New service: take `CircuitBreakerRegistry` in the constructor and use a new named breaker. The registry's default config from `ResilienceConfig.defaultConfig()` applies automatically.
- Anything per-account and user-facing should check ownership the way `revealAccountNumber()` does with `isOwnedBy`.
- Keep the 90% per-class line coverage gate in mind: `service` is one of the gated packages in `build.gradle`.

## Key source files

| File | Why read it |
| --- | --- |
| `src/main/java/com/mockingbirdbank/service/AccountService.java` | Holder resolution, reveal, `protectedCall` comment |
| `src/main/java/com/mockingbirdbank/service/TransactionService.java` | Minimal example of the pattern |
| `src/main/java/com/mockingbirdbank/resilience/ServiceUnavailableException.java` | Why open-breaker failures are a separate type |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | Ownership and open-breaker tests |
| `src/test/java/com/mockingbirdbank/resilience/CircuitBreakerBehaviorTest.java` | Real trip and recovery against a failing repository |
