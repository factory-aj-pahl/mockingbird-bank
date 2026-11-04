# Features

Mockingbird Bank has a small set of user-visible features, all read-only. A holder signs in, sees a dashboard of their accounts, opens one to read its transactions, and can reveal a full account number on request. Two operational features (feature flags and the build version label) change what those screens show without a code change.

## Feature list

| Feature | Summary | Main code |
| --- | --- | --- |
| [Sign-in](sign-in.md) | Vaadin `LoginOverlay` in front of Spring Security form login, BCrypt passwords, `user.login` analytics events, sign-out from the top bar. | `src/main/java/com/mockingbirdbank/ui/view/LoginView.java`, `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` |
| [Dashboard](dashboard.md) | Landing page at `/` and `/accounts`: total assets, primary account line, one card per account, optional welcome banner, and a "Show account numbers" toolbar. | `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` |
| [Account detail](account-detail.md) | `/accounts/{id}`: account header, current balance, and a transaction grid sorted newest first. | `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` |
| [Account number reveal](account-number-reveal.md) | Eye button that fetches one full account number after an ownership check, re-masks after 30 seconds, plus a reveal-all toggle on the dashboard. | `src/main/java/com/mockingbirdbank/ui/view/AccountNumberDisplay.java` |
| [Feature flags](feature-flags.md) | Four boolean flags under `mockingbird.feature-flags.*`, overridable per environment by env var and visible at `/actuator/featureflags`. | `src/main/java/com/mockingbirdbank/config/FeatureFlagService.java` |
| [Build version](build-version.md) | The running version and commit, shown under the wordmark, in `/actuator/info`, and in a startup log line. | `src/main/java/com/mockingbirdbank/observability/BuildVersion.java` |

The shared top bar (`src/main/java/com/mockingbirdbank/ui/layout/MainLayout.java`) appears on every signed-in screen. It holds the wordmark, the build version label, the "Welcome back" greeting, the dark mode toggle and the Sign out button. The layout itself is covered in [UI](../systems/ui.md).

## Which features touch which systems

```mermaid
graph LR
    SignIn[Sign-in] --> Security[Spring Security]
    SignIn --> Analytics[AnalyticsEventService]
    Dashboard --> AccountSvc[AccountService]
    Dashboard --> Flags[FeatureFlagService]
    Dashboard --> Analytics
    Detail[Account detail] --> AccountSvc
    Detail --> TxSvc[TransactionService]
    Detail --> Analytics
    Detail --> Flags
    Reveal[Account number reveal] --> AccountSvc
    Reveal --> Flags
    AccountSvc --> Analytics
    AccountSvc --> Breaker[Circuit breakers]
    TxSvc --> Breaker
    Version[Build version] --> Flags
```

| Feature | [Services](../systems/services.md) | [Analytics](../systems/analytics.md) | [Resilience](../systems/resilience.md) | Flags | [Security](../security.md) |
| --- | --- | --- | --- | --- | --- |
| Sign-in | `AccountService.currentHolder` (top bar greeting) | `user.login` | no | no | form login, BCrypt |
| Dashboard | `AccountService` | `dashboard.viewed` | `accounts` breaker | `showWelcomeBanner`, `showAccountNumberOnDashboard`, `allowAccountNumberReveal` | `@PermitAll` route |
| Account detail | `AccountService`, `TransactionService` | `account.viewed` | `accounts`, `transactions` breakers | `allowAccountNumberReveal` | `@PermitAll` route, no ownership check |
| Account number reveal | `AccountService.revealAccountNumber(s)` | `account_number.revealed` | `accounts` breaker | `allowAccountNumberReveal` | ownership check |
| Build version | none | no | no | `showBuildVersion` | `/actuator/info` is public |

## Where tests cover features

- Browser flow: `qa/tests/dashboard.spec.ts` (Playwright) signs in, checks the dashboard and one account's transactions, signs out, checks a wrong password is rejected, exercises the eye toggles, and checks the build version label position. Run it with `./scripts/run-interactive-qa.sh`.
- Service logic: `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` and `src/test/java/com/mockingbirdbank/service/TransactionServiceTest.java`.
- Login over HTTP: `src/test/java/com/mockingbirdbank/ApplicationSmokeTest.java`.

The Vaadin views themselves have no unit tests. JaCoCo tracks but does not gate coverage on `com.mockingbirdbank.ui.*` (see [Testing](../how-to-contribute/testing.md)).
