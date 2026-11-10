# Patterns and conventions

These are the rules the code follows and the tooling enforces. Most come from the "Conventions" section of `AGENTS.md`. The rest are visible in nearly every class.

## Layout

- **Package by layer**, not by feature: `model`, `repository`, `service`, `security`, `ui`, plus `config`, `analytics`, `resilience`, `observability` under `src/main/java/com/mockingbirdbank/`. New classes go in the matching layer. Tests mirror the same packages under `src/test/java/com/mockingbirdbank/`.
- **UI is split** into `ui/layout/` (the shared `MainLayout` and `BirdMark`) and `ui/view/` (routes and their components).

## Dependency injection

Constructor injection only, never `@Autowired` fields. Every `@Service` and `@Component` takes its dependencies as `final` constructor parameters, for example `AccountService(AppUserRepository, AccountRepository, CircuitBreakerRegistry, AnalyticsEventService)` in `src/main/java/com/mockingbirdbank/service/AccountService.java`. Vaadin views get beans the same way.

Fields holding non-serializable Spring objects in Vaadin components are `transient`, as with `AuthenticationContext` in `src/main/java/com/mockingbirdbank/ui/layout/MainLayout.java`.

## Entities

- Lombok `@Getter`, `@Setter` and `@NoArgsConstructor` on every entity, plus one explicit constructor with the required fields. No hand-written accessors.
- Display logic that depends only on the entity lives on it: `Account.getMaskedNumber()`, `Account.getDisplayName()`, `Transaction.isCredit()`.
- Any `@OneToMany` that may be iterated across several parents gets `@BatchSize` (see `Account.transactions`). `AccountTransactionQueryPerformanceTest` guards against N+1 queries.
- Schema changes go in a new Flyway migration (the next is `V3__...`). Never edit a migration that may have run somewhere.

## Services

- Services are `@Transactional(readOnly = true)` at class level.
- Every repository call goes through a private `protectedCall(Supplier)` that runs it inside the service's named circuit breaker. Only the repository call is wrapped. Business outcomes such as `orElseThrow` for a missing account stay outside, so they do not count as breaker failures.
- "Not found" is `IllegalArgumentException`; "not yours" is Spring Security's `AccessDeniedException`; "breaker open" is `ServiceUnavailableException`. The reveal path fails the same way for missing and foreign accounts, so callers cannot probe which IDs exist.

## Error handling and signals

- User-facing failures are tracked, not just logged: `analytics.trackError(event, reason, attributes)` with a short, low-cardinality `reason` (`not_found`, `forbidden`, an exception simple name).
- Views catch only what they can turn into a better UX. `AccountDetailView` converts a missing account into `NotFoundException`. `AccountNumberDisplay` shows a notification when a reveal fails. Everything else falls through to `GlobalErrorHandler`, which tracks `ui.unhandled_exception` and then delegates to Vaadin's `DefaultErrorHandler`.
- Never log secrets or full account numbers. `ScrubbingMessageConverter` is a safety net, not permission.

See [Analytics](../systems/analytics.md) and [Resilience](../systems/resilience.md).

## Feature flags

UI-facing optional behavior goes behind a flag. Adding one takes two edits: a field in `src/main/java/com/mockingbirdbank/config/FeatureFlagsProperties.java` whose default matches current behavior, and an accessor on `FeatureFlagService` (also added to `allFlags()`). Call sites ask `featureFlagService.someFlag()`, never the properties bean. See [Feature flags](../features/feature-flags.md).

## Formatting and style

| Rule | Enforced by |
| --- | --- |
| google-java-format (AOSP indentation, 4 spaces) | Spotless (`./gradlew spotlessApply`) |
| No wildcard imports (`AvoidStarImport`), naming, file length, TODO tracking | Checkstyle, `config/checkstyle/checkstyle.xml` |
| No unused fields, parameters, locals or private methods | PMD, `config/pmd/dead-code.xml` |
| No copy-pasted blocks | CPD |
| 90% line coverage per class in business packages, 30% overall | JaCoCo |

The pre-commit hook runs Spotless and Checkstyle. See [Tooling](tooling.md).

## Comments

Comments explain why, not what. Many classes have a Javadoc paragraph on the reason the class exists (see `ResilienceConfig`, `AnalyticsEventService`, `ScrubbingMessageConverter`). Inline comments cover non-obvious constraints, such as why `MainLayout` needs `@PermitAll` or why `AccountNumberDisplay`'s eye button calls `preventDefault()` inside an `<a>`.

## Tests

- Unit tests use Mockito for services and plain JUnit for entities.
- Integration tests use `@SpringBootTest` with a Testcontainers `postgres:16-alpine`.
- Tests reuse production constants rather than copying them: `DataInitializer.DEV_USERNAME`, `ResilienceConfig.defaultConfig()`.

See [Testing](testing.md).

## Commits and PRs

Commit subjects are imperative and end with the Linear key and PR number when there is one: `Add show/hide eye toggles for full account numbers (SED-649) (#1)`. AI-assisted commits carry a `Co-authored-by: factory-droid[bot]` trailer. See [Development workflow](development-workflow.md).
