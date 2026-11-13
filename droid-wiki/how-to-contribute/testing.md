# Testing

The suite has 24 JUnit classes (109 test methods) under `src/test/java/com/mockingbirdbank/`, mirroring the main packages, plus a 4-test Playwright suite in `qa/` that drives a real browser. `./gradlew test` runs the JUnit side and needs Docker for the Testcontainers-backed classes. On top of that, a Droid skill does diff-driven functional QA in a real browser, locally with `/qa` and on pull requests through `.github/workflows/qa.yml` (see [Functional QA](functional-qa.md)).

## Test layers

```mermaid
graph TD
    A["Unit tests: Mockito and plain JUnit"] --> B["./gradlew test"]
    C["Integration: SpringBootTest + Testcontainers postgres:16-alpine"] --> B
    B --> D["timings.txt and flaky.txt"]
    B --> E["JaCoCo report and thresholds"]
    F["Playwright suite in qa"] --> G["scripts/run-interactive-qa.sh"]
    G --> H["bootRun against throwaway Postgres on 5433"]
    Q["Droid qa skill"] --> R["/qa locally, qa.yml on PRs"]
    R --> S["QA Report: flows picked from the diff"]
```

### Unit tests

Most classes are plain JUnit 5 or `@ExtendWith(MockitoExtension.class)` tests that construct the class under test directly. They reuse production constants rather than copying them, for example `ResilienceConfig.defaultConfig()` and `DataInitializer.DEV_USERNAME`. Entities (`AccountTest`, `TransactionTest`) are tested without Spring at all.

### Integration tests

Two classes boot the full Spring context with `@SpringBootTest` against a real `postgres:16-alpine` started by Testcontainers (`@Testcontainers`, `@Container @ServiceConnection`). Flyway migrates the empty container database and `DataInitializer` seeds it, as in a real first start. A third, `ProgramMigrationTest`, uses Testcontainers without Spring.

- `src/test/java/com/mockingbirdbank/ApplicationSmokeTest.java` uses MockMvc to check `/actuator/health`, that `/actuator/info` is public and limited to build and git fields, that seeding created two holders (three accounts for `jordan.ellis`, one for `riley.chen`), that each seeded holder takes part in one program whose rules are met, that `/accounts` redirects anonymous users to `/login`, and that form login succeeds or fails as expected for both seeded users.
- `src/test/java/com/mockingbirdbank/service/AccountTransactionQueryPerformanceTest.java` is the N+1 guard. It enables Hibernate statistics, seeds 6 accounts with 2 transactions each, clears the session, loads the accounts and touches every `transactions` collection, then asserts fewer than 6 prepared statements ran. Removing `@BatchSize` from `Account.transactions` makes it fail. See [Persistence](../systems/persistence.md).
- `src/test/java/com/mockingbirdbank/ProgramMigrationTest.java` drives Flyway directly (through the `flyway-core` test dependency) against its own `postgres:16-alpine`. It migrates a fresh schema to V1, inserts a holder and an account, migrates on, and checks the account is `USD` with no program. It also checks that the database rejects linking an account to another customer's program (SQLSTATE `23503`) and rejects an unknown program type or a lower-case currency code (`23514`).

### Circuit breaker behavior

`src/test/java/com/mockingbirdbank/resilience/CircuitBreakerBehaviorTest.java` builds `AccountService` and `TransactionService` with a real `CircuitBreakerRegistry.of(ResilienceConfig.defaultConfig())` and a repository mock that always throws `DataAccessResourceFailureException`. It drives the `accounts` breaker through `accountsFor(holder)` and the `transactions` breaker through `forAccount`. The first 10 calls reach the repository; the 11th fails fast with `ServiceUnavailableException` and the repository call count stays at 10. It does not test the half-open recovery after 10 seconds. See [Resilience](../systems/resilience.md).

### The login smoke test stops early on purpose

`ApplicationSmokeTest.seededUserCanLogIn` asserts that login redirects to `/` and that the session holds an authenticated `jordan.ellis` principal. It deliberately does not follow up with `GET /accounts`. Under MockMvc, Vaadin's servlet initializes lazily on the first real dispatch, so `RequestUtil.isSecuredFlowRoute()` treats the route as unsecured and the default-deny rule returns 403 even for an authenticated request. This is a test-harness artifact, recorded as a "Known non-issue" in `AGENTS.md`. Do not "fix" it by asserting 200.

### Browser QA with Playwright

`./scripts/run-interactive-qa.sh` exercises the real frontend:

1. Starts a throwaway Postgres container `mockingbird-qa-postgres` on port 5433 (image `postgres:17.2`), so it never touches a database on 5432. A shared database with unrelated tables would trip Flyway's `baseline-on-migrate` and skip V1.
2. Runs `./gradlew bootRun` against it and waits up to 180 seconds for `/actuator/health` to report `UP`.
3. Runs `npx playwright test` in `qa/` (Chromium only, `retries: 0`, base URL from `QA_BASE_URL`, default `http://localhost:8080`).
4. Stops the app and removes the container on exit, printing the last 80 app log lines on failure.

The four tests in `qa/tests/dashboard.spec.ts`:

| Test | Checks |
| --- | --- |
| signing in shows the seeded accounts and their transactions | Redirect to `/login`, greeting, three account cards, account detail rows, sign-out |
| a wrong password is rejected | URL becomes `/login?error` |
| eye toggles reveal and re-mask full account numbers | Per-card eye and the "Show all account numbers" toggle |
| the build version shows directly under the wordmark | `#build-version` text format, tooltip, position under the wordmark |

One-time setup: `cd qa && npm install && npx playwright install chromium`. The suite is not run by CI.

### Functional QA with Droid

`.factory/skills/qa/SKILL.md` and `.factory/skills/qa-web/SKILL.md` let Droid test a change as a user would. The agent reads the git diff, maps changed paths to flows (W1 to W15: sign-in, dashboard, detail, reveal, build version, dark mode, sign-out, unknown and cross-account access, Actuator access, flag variants, database outage, account programs), drives the browser through the `droid-control` plugin, and writes `qa-results/report.md`. It does not run this suite or any other automated tests, and it never replaces `./gradlew build`. `.github/workflows/qa.yml` runs it on non-draft same-repository PRs that touch app code and keeps one sticky "QA Report" comment updated. The check is required on `master`, but its job passes whatever QA finds, so read the comment. Details are in [Functional QA](functional-qa.md).

## Running tests

```bash
./gradlew test                                                        # everything, Docker required
./gradlew test --tests "com.mockingbirdbank.service.AccountServiceTest"
./gradlew test --tests "com.mockingbirdbank.service.AccountServiceTest.requireAccountThrowsForUnknownId"
./gradlew jacocoTestReport                                            # HTML coverage report
./scripts/run-interactive-qa.sh                                       # browser suite
```

Console output lists every test as PASSED, FAILED or SKIPPED with full exception traces (`testLogging` in `build.gradle`). The HTML report is `build/reports/tests/test/index.html`.

## Retries and flaky tests

The `org.gradle.test-retry` plugin (1.6.2) re-runs a failed test up to 2 more times (`maxRetries = 2`). If more than 10 tests fail (`maxFailures = 10`) retries stop, since that points to a real break. `failOnPassedAfterRetry = false` means a test that fails and then passes does not fail the build.

To keep that instability visible, `build.gradle` records every attempt's result per test and writes `build/reports/tests/test/flaky.txt` after each run. It lists any test whose attempts disagreed, or says "No flaky tests detected in this run". A Gradle warning `FLAKY TEST(S) DETECTED` is also logged.

## Timings

Every run writes `build/reports/tests/test/timings.txt`, all test methods sorted slowest first, and logs the slowest one. Any test over 10,000 ms logs a `SLOW TEST` warning. The build never fails on time; the Testcontainers classes legitimately spend seconds on container startup.

## Coverage gates

JaCoCo 0.8.12 runs as part of `check`. `jacocoTestReport` writes XML and HTML (`build/reports/jacoco/test/html/index.html`), and `jacocoTestCoverageVerification` enforces two rules:

| Rule | Scope | Minimum |
| --- | --- | --- |
| Project-wide line coverage | Everything | 30% |
| Per-class line coverage | `com.mockingbirdbank.model.*`, `service.*`, `security.*`, `config.*`, `analytics.*`, `resilience.*`, `observability.*` | 90% |

`com.mockingbirdbank.ui.*`, `com.mockingbirdbank.repository.*` and `Application` are outside the per-class rule. UI code still counts toward the 30% floor. The reasoning, in `build.gradle`, is that Vaadin views are declarative and not meaningfully unit-testable without a browser fixture. A new class in a gated package needs at least 90% line coverage or the build fails.

## Test classes

| Class | Kind | Covers |
| --- | --- | --- |
| `src/test/java/com/mockingbirdbank/ApplicationSmokeTest.java` | Integration | Context boot, health, info, seeding of both holders and their programs, login redirect, form login for both users |
| `src/test/java/com/mockingbirdbank/ProgramMigrationTest.java` | Integration, Flyway without Spring | V1 data surviving V2, cross-customer program link rejected, program type and currency checks |
| `src/test/java/com/mockingbirdbank/service/AccountTransactionQueryPerformanceTest.java` | Integration | N+1 guard on `Account.transactions` |
| `src/test/java/com/mockingbirdbank/resilience/CircuitBreakerBehaviorTest.java` | Unit, real breaker | The `accounts` and `transactions` breakers trip after 10 failures and fail fast |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | Mockito | `currentHolder`, `totalBalance`, owner-only `requireAccount` (own, unknown, someone else's), reveal ownership checks, reveal-all, reveal when breaker is open |
| `src/test/java/com/mockingbirdbank/service/TransactionServiceTest.java` | Mockito | `forAccount` delegates to the newest-first query |
| `src/test/java/com/mockingbirdbank/service/ProgramServiceTest.java` | Mockito, fixed clock | Regular balance boundary (`@ParameterizedTest`), account composition, non-USD savings, Youth Savings age boundaries, New York versus UTC date, missing and future date of birth, leap-day birthday, cross-customer account, open `programs` breaker |
| `src/test/java/com/mockingbirdbank/model/AccountTest.java` | JUnit | Masking, short or missing numbers, display name fallback |
| `src/test/java/com/mockingbirdbank/model/TransactionTest.java` | JUnit | `isCredit` for positive, negative and null amounts |
| `src/test/java/com/mockingbirdbank/security/AppUserDetailsServiceTest.java` | Mockito | User mapping with `ROLE_USER`, unknown user |
| `src/test/java/com/mockingbirdbank/analytics/AnalyticsEventServiceTest.java` | JUnit + `SimpleMeterRegistry` | Counter tags, accumulation, correlation ID in event lines |
| `src/test/java/com/mockingbirdbank/analytics/AuthenticationEventListenerTest.java` | JUnit | Login success and failure events |
| `src/test/java/com/mockingbirdbank/analytics/GlobalErrorHandlerTest.java` | JUnit | Session error handler tracks then delegates |
| `src/test/java/com/mockingbirdbank/observability/CorrelationIdFilterTest.java` | JUnit | Generate, reuse, MDC cleared on exception |
| `src/test/java/com/mockingbirdbank/observability/ProfilingEndpointTest.java` | JUnit | Real JFR file written, status, double start, stop with nothing running |
| `src/test/java/com/mockingbirdbank/observability/BuildVersionTest.java` | JUnit | Version, commit, branch, display format, missing metadata, startup log |
| `src/test/java/com/mockingbirdbank/config/FeatureFlagServiceTest.java` | JUnit | Defaults, overrides, `allFlags()` |
| `src/test/java/com/mockingbirdbank/config/FeatureFlagsEndpointTest.java` | JUnit | Endpoint delegates to the service |
| `src/test/java/com/mockingbirdbank/config/BusinessClockConfigTest.java` | JUnit | The business clock runs on America/New_York |
| `src/test/java/com/mockingbirdbank/ui/view/AccountDetailViewTest.java` | Mockito + Vaadin route registry | Any single segment under `accounts` routes to the view; non-numeric, empty, out-of-range and non-ASCII-digit IDs give the not-found page with no service call; unknown or foreign ID; own account |
| `src/test/java/com/mockingbirdbank/ui/view/DashboardViewTest.java` | Mockito | `showAccountCount` line (singular, plural, no extra `AccountService` calls, absent when the flag is off) and `showAccountPrograms` (off, cards and notice, no notice when met, none, unavailable for an open breaker and for a database error) |
| `src/test/java/com/mockingbirdbank/ui/view/ProgramCardTest.java` | JUnit | Program card links, status pill, unmet rules |
| `src/test/java/com/mockingbirdbank/ui/view/ProgramLookupTest.java` | JUnit | Program read fallback: open breaker and database errors give an empty result and a `programs.unavailable` count, other exceptions propagate |
| `src/test/java/com/mockingbirdbank/config/logging/ScrubbingMessageConverterTest.java` | JUnit | Password, token, Bearer header, card numbers, null and empty input |

## Known gaps

- `AccountDetailViewTest`, `DashboardViewTest`, `ProgramCardTest` and `ProgramLookupTest` are the only JUnit tests in `com.mockingbirdbank.ui`. `AccountDetailViewTest` does not cover the program section; its fallback is `ProgramLookup`, which `ProgramLookupTest` covers. `AccountNumberDisplay` has no unit test, and its 30-second auto-hide is not covered by Playwright either.
- `CircuitBreakerBehaviorTest` checks tripping only, not recovery through half-open, although `AGENTS.md` says it verifies both.
- `AccountServiceTest.requireAccountRejectsAccountOwnedBySomeoneElseLikeAnUnknownOne` covers the ownership check at the service level, but no JUnit or Playwright test drives `/accounts/{id}` for another holder's account end to end, and the Playwright suite signs in only as `jordan.ellis`. The Droid QA flows W11 (as `jordan.ellis` against a QA-seeded holder) and W15 step 6 (as `riley.chen` against one of Jordan's accounts) probe that path in a browser. See [Security](../security.md#account-detail-ownership-check) and [Functional QA](functional-qa.md#flow-menu).
- The Playwright suite runs only on demand. The Droid QA workflow runs flows on PRs that touch app code, and although its check is required on `master`, the job never fails on a QA finding. The post-publish smoke test in `.github/workflows/publish-image.yml` checks only that the image boots and is healthy.
- The QA script uses `postgres:17.2` while Testcontainers, `docker-compose.yml` and k3d use `postgres:16-alpine`.

See [Debugging](debugging.md) for reading test failures, and [Tooling](tooling.md) for the Gradle wiring.
