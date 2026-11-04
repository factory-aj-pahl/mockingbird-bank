# Design decisions

The choices below shape most of the codebase. Each section gives the context, what was decided, what follows from it, and where the evidence lives (a commit hash, a code comment or a config file). The timeline of these commits is on the [lore](../lore.md) page.

## Server-side Vaadin Flow, no REST layer

**Context.** The app is a read-only dashboard for one signed-in holder. It needs a login page, a dashboard and an account detail page.

**Decision.** Every view is a Java Vaadin Flow class under `src/main/java/com/mockingbirdbank/ui/`. Views receive `AccountService` and `TransactionService` by constructor injection and call them in-process. There is no JSON API for the frontend; the only HTTP endpoints besides Vaadin's own are Actuator's.

**Consequences.**
- No DTOs, controllers or client-side state management to maintain. Access control is expressed with `@PermitAll` / `@AnonymousAllowed` on route classes.
- UI state lives in server memory per session, which is what forces the single-replica deployment below.
- View code is hard to unit test without a browser fixture, so the `ui` packages are excluded from the per-class JaCoCo gate (see the comment above `jacocoTestCoverageVerification` in `build.gradle`).
- Although `AGENTS.md` and `docs/architecture.md` say "Flow + React", the repository has no `.tsx` or `.jsx` files. `src/main/frontend/` holds only the theme.

**Evidence.** `docs/architecture.md` ("There is no separate REST/JSON API"), `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java`, `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java`. See [UI](../systems/ui.md) and [API](../api.md).

## One process, one replica

**Context.** Vaadin Flow keeps each user's component tree on the server.

**Decision.** Both Kubernetes shapes run `replicas: 1` (`k8s/03-app.yaml`, `k8s/local/app.yaml`). There is no cache, queue or second service.

**Consequences.** Scaling out would need sticky sessions or session replication, and a pod restart signs everyone out. Rollouts briefly drop sessions. In exchange there is nothing to coordinate between instances.

**Evidence.** `AGENTS.md` (k3d section: "one replica; Vaadin sessions live in memory, so scaling out would need sticky sessions"), `docs/architecture.md` ("intentionally a single-service application").

## Flyway over ddl-auto, with baseline-on-migrate

**Context.** The scaffold let Hibernate create tables with `ddl-auto: update`.

**Decision.** Commit `b64da02` replaced it with Flyway. Hibernate now runs `ddl-auto: validate`, and `V1__init_schema.sql` mirrors exactly what Hibernate had been generating. `spring.flyway.baseline-on-migrate: true` lets databases created before that commit adopt Flyway without V1 failing on "relation already exists".

**Consequences.**
- Schema changes are reviewed SQL with history. Hibernate refuses to start if an entity drifts from the schema.
- Spring Boot 4 moved Flyway auto-configuration into `spring-boot-starter-flyway`, and Flyway 10 split out Postgres support; both had to be declared explicitly (`b64da02` message).
- `baseline-on-migrate` also treats any non-empty schema as "already at V1". That bit the LAB deployment (see [pitfalls](pitfalls.md#baseline-on-migrate-skips-v1-on-a-non-empty-database)).

**Evidence.** `b64da02`, `src/main/resources/application.yml` (comment above `baseline-on-migrate`), `src/main/resources/db/migration/V1__init_schema.sql`. See [persistence](../systems/persistence.md).

## AppUser separate from AccountHolder

**Context.** Commit `60f9881` added form login and replaced a "grab the first row" placeholder in `AccountService.currentHolder()`.

**Decision.** Credentials (username, BCrypt hash) live in their own `app_user` table, one-to-one with `account_holder` through a unique `holder_id`. The holder carries the banking and PII data (name, email).

**Consequences.** Authentication code never touches holder data, and holder queries never load password hashes. `currentHolder()` resolves the principal's username to an `AppUser`, then to its holder. There is no registration flow; the only user is the seeded one.

**Evidence.** Javadoc on `src/main/java/com/mockingbirdbank/model/AppUser.java`, commit `60f9881`.

## Package by layer

**Decision.** Code is split into `model`, `repository`, `service`, `security`, `ui`, plus cross-cutting `config`, `analytics`, `resilience` and `observability`. New classes follow the existing layout rather than grouping by feature.

**Consequences.** With three screens, a feature like account-number reveal touches `service`, `ui/view` and `config` at once; that cost is small at this size. The layer packages also line up with the JaCoCo gate list in `build.gradle` and the `area:` labels in `.github/labeler.yml`.

**Evidence.** `AGENTS.md` "Conventions". See [patterns and conventions](../how-to-contribute/patterns-and-conventions.md).

## Resilience4j wired by hand

**Context.** Commit `25c37cd` added circuit breakers around repository calls.

**Decision.** The app depends on the plain `resilience4j-circuitbreaker` and `resilience4j-micrometer` artifacts (pinned at 2.4.0) and builds a `CircuitBreakerRegistry` bean itself in `ResilienceConfig`. Services call `circuitBreaker.executeSupplier(...)` in a private `protectedCall` helper; there are no annotations.

**Consequences.** No dependency on `resilience4j-spring-boot3`, which `build.gradle` notes "targets Spring Boot 3's actuator/autoconfigure APIs, not guaranteed compatible with Spring Boot 4 yet". The tuning is code, not YAML, and `ResilienceConfig.defaultConfig()` is static so `CircuitBreakerBehaviorTest` exercises the same numbers that ship. There is no Actuator `circuitbreakers` endpoint; state is only visible through Micrometer meters.

**Evidence.** `build.gradle` (comment above the resilience4j dependencies), `src/main/java/com/mockingbirdbank/resilience/ResilienceConfig.java`. See [resilience](../systems/resilience.md).

## Homegrown analytics instead of a SaaS

**Context.** Commit `d263f9d` wanted product events and user-facing errors to be countable without tailing logs.

**Decision.** `AnalyticsEventService` increments one Micrometer counter (`product.events`, tagged `event`/`outcome`/`reason`) and writes one key=value line to the `com.mockingbirdbank.analytics.events` logger, which `logback-spring.xml` routes to `build/logs/analytics-events.log` (daily roll, 30 days kept). Login outcomes and unhandled UI exceptions feed it automatically via `AuthenticationEventListener` and `GlobalErrorHandler`.

**Consequences.** No vendor SDK or outbound traffic. A Micrometer registry or a log shipper can be added later without touching call sites. Today the counter is only readable in-process, because `/actuator/metrics` is not exposed (see [cleanup opportunities](../cleanup-opportunities.md#docs-drift)).

**Evidence.** Class Javadoc of `src/main/java/com/mockingbirdbank/analytics/AnalyticsEventService.java` ("queryable without any external SaaS"), commit `d263f9d`. See [analytics](../systems/analytics.md).

## JFR profiling instead of an APM agent

**Decision.** `ProfilingEndpoint` exposes `/actuator/profiling` (authenticated): `GET` reports status, `POST` starts a JDK Flight Recorder recording with the `default` configuration, `DELETE` stops it and writes a `.jfr` file under `mockingbird.profiling.output-dir`.

**Consequences.** No extra agent, license or JVM flag. The output is opened offline in JDK Mission Control or VisualVM. Only one recording runs at a time, and the file stays on the pod's local disk.

**Evidence.** `src/main/java/com/mockingbirdbank/observability/ProfilingEndpoint.java` (Javadoc mentions the APM alternative), commit `25c37cd`. See [observability](../systems/observability.md).

## Centralized log scrubbing

**Context.** Until `00b53d6`, `DataInitializer` logged the seeded demo password in plain text. The commit message points out that anyone setting `MOCKINGBIRD_DEV_PASSWORD` to a reused password would have leaked it.

**Decision.** `logback-spring.xml` replaces the `%msg`/`%m` conversion words with `ScrubbingMessageConverter`, which redacts `Authorization: Bearer/Basic` values, password/secret/token/API-key `key=value` pairs and 13-19 digit card-like numbers in every message.

**Consequences.** Protection does not depend on each call site remembering. The cost is a few regex passes per log line, and fixtures that look like secrets in tests trip gitleaks (see [pitfalls](pitfalls.md#gitleaks-flags-test-fixtures)).

**Evidence.** `src/main/java/com/mockingbirdbank/config/logging/ScrubbingMessageConverter.java`, `src/main/resources/logback-spring.xml`, commit `00b53d6`.

## Feature flags bound from config, defaulting to current behavior

**Decision.** `FeatureFlagsProperties` is a `@ConfigurationProperties("mockingbird.feature-flags")` bean with one boolean per flag. Each default matches what the app did before the flag existed, so adding a flag changes nothing until an environment overrides it with `MOCKINGBIRD_FEATURE_FLAGS_<NAME>`. `FeatureFlagService` is the typed API views call, and `/actuator/featureflags` shows current values read-only.

**Consequences.** No flag server, no per-user targeting and no runtime toggling: values bind at startup. Defaults are written twice (Java field initializers and `application.yml`) with nothing checking they agree.

**Evidence.** `src/main/java/com/mockingbirdbank/config/FeatureFlagsProperties.java`, commit `a8e08e8`. See [feature flags](../features/feature-flags.md).

## The showBuildVersion exception

**Decision.** `showBuildVersion` defaults to `true`, even though it was new behavior when added in `736990a`. The field's Javadoc explains: knowing what is deployed is useful in every environment, and the top bar only appears after sign-in.

**Consequences.** Every environment shows the version label under the wordmark unless it sets `MOCKINGBIRD_FEATURE_FLAGS_SHOW_BUILD_VERSION=false`. The rule in `FeatureFlagsProperties` now reads "unless its own comment says otherwise".

**Evidence.** `src/main/java/com/mockingbirdbank/config/FeatureFlagsProperties.java`, commit `736990a`. See [build version](../features/build-version.md).

## Public /actuator/info limited to build and git

**Decision.** `/actuator/info` is `permitAll` in `SecurityConfig`, but `application.yml` sets `management.info.defaults.enabled: false` and turns back on only the `build` contributor and the `git` contributor in `simple` mode.

**Consequences.** Probes and humans can ask "what's deployed?" without a session, and a contributor that a future Spring Boot version adds stays off. The ZAP baseline scan reports an information-disclosure finding for it; `AGENTS.md` lists that as expected.

**Evidence.** `src/main/resources/application.yml` (comment under `management.info`), `src/main/java/com/mockingbirdbank/security/SecurityConfig.java`, commit `736990a`.

## External Postgres for LAB, in-cluster Postgres for k3d

**Context.** The scaffold shipped an in-cluster Postgres StatefulSet. The LAB cluster already had an external Postgres behind the Harness `postgresdb` connector.

**Decision.** `278a4eb` deleted the in-cluster database from `k8s/` and pointed the app at the external host, with credentials resolved by Harness from its secret manager (via `k8s/values.yaml` after `b3f9fd0`). Later, `5fbf51f` added `k8s/local/`, a plain kustomize tree with its own `postgres:16-alpine` StatefulSet on a `local-path` PVC and throwaway credentials, for the local k3d cluster only.

**Consequences.** Two independent manifest sets that are not interchangeable: Harness Go templates in `k8s/*.yaml`, kustomize in `k8s/local/`. The LAB database must be created by hand once with `scripts/bootstrap-lab-db.sql`, because Flyway cannot create a database.

**Evidence.** Commits `278a4eb`, `b3f9fd0`, `b809e47`, `5fbf51f`. See [LAB cluster](../deployment/lab-cluster.md) and [local k3d](../deployment/local-k3d.md).

## CI split into fast-checks and test

**Decision.** `.github/workflows/ci.yml` runs two parallel jobs. `fast-checks` compiles and runs Spotless, Checkstyle, PMD, CPD, `buildHealth`, `validateAgentsMd` and Javadoc without Docker. `test` runs `./gradlew build` with those same tasks excluded via `-x`, so the Testcontainers suite is the long pole.

**Consequences.** Lint failures show up in under a minute. A green `test` job alone does not mean `./gradlew build` passes locally, since it skipped the lint tasks; both jobs must pass. The code-quality summary artifact is split the same way.

**Evidence.** Commit `a8e08e8` ("fast_ci_feedback"), `.github/workflows/ci.yml`.

## Test retry instead of blocking on flaky tests

**Decision.** The Gradle test-retry plugin reruns a failing test up to two more times with `failOnPassedAfterRetry = false`. Every attempt's outcome is recorded, and any test whose attempts disagree is listed in `build/reports/tests/test/flaky.txt`.

**Consequences.** A flaky Testcontainers test does not block a merge, but the instability is written down instead of hidden. Nothing fails the build if `flaky.txt` grows; someone has to read it.

**Evidence.** Commit `9d42417`, `build.gradle` (`retry { maxRetries = 2 ... }` and the comment above it).

## Dependabot cooldown

**Decision.** Every ecosystem in `.github/dependabot.yml` has a `cooldown`: 5 days by default, 3 for patch, 7 for minor and 14 for major bumps (Docker and GitHub Actions set fewer tiers).

**Consequences.** A freshly published release, the window when a supply-chain compromise or regression usually surfaces, never arrives as a PR on day one. Urgent security fixes still wait the cooldown unless someone bumps by hand.

**Evidence.** Comment in `.github/dependabot.yml`, commit `00b53d6`.
