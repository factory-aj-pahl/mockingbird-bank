# Systems

Active contributors: ajpahl1008 (AJ Pahl)

The application code lives in nine packages under `src/main/java/com/mockingbirdbank/`, organized by layer rather than by feature. This page maps each package to its page in the wiki and shows which packages are allowed to depend on which.

## Package map

| Package | Main files | Main lines | Test files | Test lines | What it holds | Covered in |
| --- | --- | --- | --- | --- | --- | --- |
| `com.mockingbirdbank` (root) | 1 | 15 | 2 | 360 | `Application` entry point with `@Theme("mockingbird")` | [UI](ui.md) |
| `ui` (`layout/`, `view/`) | 8 | 1,099 | 2 | 281 | Vaadin routes, the shared top bar, account cards, the number reveal component, program cards | [UI](ui.md) |
| `service` | 4 | 423 | 4 | 679 | `AccountService`, `TransactionService`, `ProgramService`, `ProgramEvaluation` | [Services](services.md) |
| `model` | 7 | 354 | 2 | 109 | JPA entities and the `AccountType` and `ProgramType` enums | [Persistence](persistence.md) |
| `repository` | 5 | 46 | 0 | 0 | Spring Data JPA interfaces | [Persistence](persistence.md) |
| `security` | 2 | 86 | 1 | 57 | `SecurityConfig`, `AppUserDetailsService` | [Security](../security.md) |
| `analytics` | 3 | 188 | 3 | 226 | Product events, error tracking, login listener, Vaadin error handler | [Analytics](analytics.md) |
| `resilience` | 2 | 59 | 1 | 82 | Circuit breaker config, `ServiceUnavailableException` | [Resilience](resilience.md) |
| `observability` | 3 | 212 | 3 | 248 | Correlation IDs, JFR profiling endpoint, `BuildVersion` | [Observability](observability.md) |
| `config` (incl. `config/logging/`) | 6 | 408 | 4 | 150 | Feature flags, `DataInitializer` seeding, `BusinessClockConfig`, `ScrubbingMessageConverter` | [Feature flags](../features/feature-flags.md), [Persistence](persistence.md#seeding), [Security](../security.md) |
| **Total** | **41** | **2,890** | **22** | **2,192** | | |

Counts are of the `.java` files under `src/main/java` and `src/test/java`, by package directory.

The test-to-code ratio is 0.76 by lines (2,192 test lines for 2,890 main lines) and 0.54 by files. That average hides a split: the `ui` package has 1,099 lines and only two unit test classes (`DashboardViewTest` for the account count line and programs section, `ProgramCardTest` for the program card), while `service` has 1.6 test lines per main line. The UI is left out of the JaCoCo per-class gate on purpose; see [UI](ui.md#testing-and-coverage) and [Testing](../how-to-contribute/testing.md).

## Layering

Arrows show actual `import com.mockingbirdbank.*` dependencies between packages in `src/main/java`.

```mermaid
graph TD
    ui[ui.view and ui.layout] --> service
    ui --> config
    ui --> analytics
    ui --> resilience
    ui --> observability
    ui --> model
    service --> repository
    service --> resilience
    service --> analytics
    service --> model
    repository --> model
    config --> repository
    security --> repository
    security -.->|LoginView only| ui
    analytics -->|MDC key| observability
```

The rules the code follows:

- Views call services, never repositories. `AccountService`, `TransactionService` and `ProgramService` each wrap every repository call in a circuit breaker, so a view that went straight to a repository would skip that protection.
- `model` and `repository` depend on nothing else in the app.
- `config.DataInitializer` and `security.AppUserDetailsService` use repositories directly. Both run outside a user-facing request path (startup seeding and login lookup), so they do not go through the services.
- `security` imports `ui.view.LoginView` because `VaadinSecurityConfigurer` needs the login view class. This is the only dependency that points back up toward the UI.
- `analytics.AnalyticsEventService` reads `CorrelationIdFilter.MDC_KEY` from `observability` so event lines carry the same correlation ID as the request's log lines.

Nothing enforces these rules at build time (there is no ArchUnit test). They hold because the codebase is small and reviewed by one owner. See [Patterns and conventions](../how-to-contribute/patterns-and-conventions.md).

## Pages in this section

- [UI](ui.md): Vaadin routes, `MainLayout`, theme and dark mode.
- [Services](services.md): the three application services and the `protectedCall` pattern.
- [Persistence](persistence.md): entities, repositories, Flyway, the N+1 guard and demo seeding.
- [Analytics](analytics.md): `AnalyticsEventService` and its two automatic feeds.
- [Resilience](resilience.md): the `accounts`, `transactions` and `programs` circuit breakers.
- [Observability](observability.md): correlation IDs, profiling and build version at code level.

For how these pieces fit together at runtime, see [Architecture](../overview/architecture.md).
