# Glossary

Terms used in the code, configuration and this wiki.

| Term | Meaning |
| --- | --- |
| Account | A checking or savings account (`src/main/java/com/mockingbirdbank/model/Account.java`). Has a full account number, nickname, balance, currency (ISO 4217 code, `USD` by default), opened date, and at most one program participation. |
| Account holder | The person who owns accounts (`src/main/java/com/mockingbirdbank/model/AccountHolder.java`). Holds name, email and an optional date of birth, kept apart from login data. |
| AppUser | Login credentials for one holder: username and BCrypt hash (`src/main/java/com/mockingbirdbank/model/AppUser.java`). One per holder. |
| Current holder | The holder linked to whoever Spring Security says is signed in, resolved by `AccountService.currentHolder()`. |
| Masked number | `•••• 4821`: the last four digits, from `Account.getMaskedNumber()`. The default display everywhere. |
| Reveal | Fetching and showing the full account number on an explicit click. Ownership-checked, tracked as `account_number.revealed`, auto-hidden after 30 seconds. |
| Display name | The account nickname, or the account type name if no nickname is set (`Account.getDisplayName()`). |
| Credit / debit | Transaction amounts are signed: positive is a credit, negative a debit (`Transaction.isCredit()`). |
| Demo login | `jordan.ellis` or `riley.chen`, both with password `mockingbird`, seeded by `DataInitializer` on an empty database. Local and dev only. |
| Program | A set of rules over some of a customer's accounts (`ProgramType`): Regular (one checking, one USD savings over $100.00) or Youth Savings (one savings account, no checking, customer under 18). Evaluated by `ProgramService` every time it is shown. See [Account programs](../features/account-programs.md). |
| Program participation | One customer's participation in one program, with the accounts linked to it (`src/main/java/com/mockingbirdbank/model/ProgramParticipation.java`, table `program_participation`). |
| Unmet rule | A customer-facing message from `ProgramService` for a program rule that is not met. It changes nothing. |
| Business date | Today's date in America/New_York, from the `businessClock` bean in `src/main/java/com/mockingbirdbank/config/BusinessClockConfig.java`. Used for the Youth Savings age rule. |
| Feature flag | A boolean under `mockingbird.feature-flags.*`, overridable with a `MOCKINGBIRD_FEATURE_FLAGS_<NAME>` env var. See [Feature flags](../features/feature-flags.md). |
| Product event | A `track()` call on `AnalyticsEventService`, such as `dashboard.viewed`. Counted in `product.events` with `outcome=success`. |
| Tracked error | A `trackError()` call with a short `reason` tag, such as `account.viewed` / `not_found`. Counted with `outcome=error`. |
| `product.events` | The Micrometer counter every analytics call increments, tagged `event`, `outcome`, `reason`. |
| Analytics event log | `build/logs/analytics-events.log`, the dedicated rolling file for analytics lines. |
| Correlation ID | Per-request ID from `X-Correlation-Id` (or a new UUID), put in SLF4J MDC by `CorrelationIdFilter` and echoed in the response. |
| Circuit breaker | Resilience4j breaker named `accounts`, `transactions` or `programs` around repository calls. When open, calls fail fast with `ServiceUnavailableException`. |
| Build version | `<baseVersion>-<short sha>[-dirty]`, such as `0.1.0-09f771a`. Shown in the top bar and at `/actuator/info`. |
| Scrubbing | Redaction of passwords, tokens, auth headers and card-like numbers from every log line by `ScrubbingMessageConverter`. |
| Flyway baseline | `baseline-on-migrate: true`, which marks V1 as applied on databases that predate Flyway. |
| LAB | The on-premises Kubernetes environment deployed through Harness, with an external Postgres and a MetalLB IP. |
| k3d / `dev-k8s` | The local Kubernetes cluster (context `k3d-dev-k8s`) that `scripts/k3d-deploy.sh` deploys to. |
| Harness | The CD tool that templates `k8s/*.yaml` using `<+...>` expressions resolved from `k8s/values.yaml`. |
| SED | The Linear team key for this project's issues (for example `SED-649`). |
| `mbb-ops` | The Slack channel that CI and post-deploy alerts post to. |
