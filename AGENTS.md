# AGENTS.md

Instructions for AI coding agents (and humans) working in this repository.

## What this is

Mockingbird Bank - a read-only account dashboard SPA. Spring Boot 4 backend with a
Vaadin 25 (Flow + React) frontend, backed by PostgreSQL. There is no public
registration; it seeds one demo account holder on first run (see
[Demo login](#demo-login-local-dev-only) below).

## Tech stack

- Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security
- Vaadin 25.3.0 (Flow + React components) for the UI - frontend build is handled
  entirely by the Vaadin Gradle plugin; there's no separate `npm run dev`.
- PostgreSQL, schema-managed by Flyway (`src/main/resources/db/migration`)
- Gradle (wrapper checked in - always use `./gradlew`, not a system-installed Gradle)

## Prerequisites

- JDK 21 available locally (Gradle's toolchain support auto-detects an installed
  JDK 21; install one via sdkman/homebrew/Corretto if `./gradlew -version` can't
  find one).
- Docker, running locally - required for the Testcontainers-backed integration
  tests (`ApplicationSmokeTest`, `AccountTransactionQueryPerformanceTest`), which
  spin up a throwaway `postgres:16-alpine` container per test class.
- A reachable PostgreSQL instance if you want to run the app itself (`bootRun`);
  not required just to build or run the unit/integration test suite.

### Dev container

`.devcontainer/` gives you all of the above pre-installed (JDK 21, Node,
Docker CLI, Postgres) without touching your host machine. In VS Code, use
"Dev Containers: Reopen in Container"; from a plain terminal:

```
npx @devcontainers/cli up --workspace-folder .
npx @devcontainers/cli exec --workspace-folder . -- ./gradlew build
```

It builds on `mcr.microsoft.com/devcontainers/java:1-21-bookworm`, adds Node
via a devcontainer feature, and uses the `docker-outside-of-docker` feature so
Testcontainers-backed tests can start sibling containers against the host's
Docker daemon (`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` in
`.devcontainer/docker-compose.yml` is what makes the reaper/sibling containers
reachable from inside the dev container). `.devcontainer/docker-compose.yml`
merges with the root `docker-compose.yml`, so the same Postgres service
definition is reused rather than duplicated - it starts automatically and
`bootRun` inside the container reaches it at `postgres:5432`. Ports 8080 and
5432 are published to the host, so `curl localhost:8080/actuator/health`
works from outside the container too.

## Build

```
./gradlew build
```

Runs compilation, the full test suite, and every code-quality check described
below (Checkstyle, Spotless, PMD, CPD) as part of `check`. This is the single
command CI and this repo's pre-commit hook both rely on.

## Run locally

The app needs a Postgres connection. The quickest way to get one with the
right credentials/database name already set up:

```
docker compose up -d
```

`docker-compose.yml` at the repo root starts a `postgres:16-alpine` matching
the defaults in `src/main/resources/application.yml` (`localhost:5432`,
database `mydatabase`, user `admin` / password `secret`), with a named volume
so data survives a restart. Stop it with `docker compose down` (add `-v` to
also drop the volume and start from an empty database next time).

Alternatively, point the app at any other reachable Postgres via env vars, or
copy `.env.example` to `.env` and adjust it if you're running via
`runDockerImage.zsh`:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/mydatabase \
SPRING_DATASOURCE_USERNAME=admin \
SPRING_DATASOURCE_PASSWORD=secret \
./gradlew bootRun
```

Without those env vars it falls back to the same defaults, so `./gradlew
bootRun` alone works once `docker compose up -d` (or any Postgres matching
those defaults) is running. Flyway migrates the schema automatically on
startup. The app listens on `:8080` (override with `SERVER_PORT`).

Or, from a completely fresh clone, one command does both steps (start
Postgres, wait for it, then `bootRun`):

```
./scripts/dev-up.sh
```

### Demo login (local/dev only)

On an empty database, `DataInitializer` seeds one account holder, a matching
login, and a few sample accounts/transactions so the dashboard has something to
show:

- Username: `jordan.ellis`
- Password: `mockingbird` (override via the `MOCKINGBIRD_DEV_PASSWORD` env var)

## Testing

```
./gradlew test
```

Includes fast unit tests (Mockito-based service/model tests) and slower
`@SpringBootTest` + Testcontainers integration tests that boot the full Spring
context against a real Postgres container - Docker must be running. Test
reports land in `build/reports/tests/test/index.html`.

Every run also writes `build/reports/tests/test/timings.txt`, a per-test-method
duration report sorted slowest-first, and logs a warning for any test over 10s
- a lightweight way to notice the suite (or one test) getting slower over time
without a dedicated test-analytics platform.

The `test` task also uses the [Gradle Test Retry
plugin](https://github.com/gradle/test-retry-gradle-plugin): a failing test is
automatically re-run up to 2 more times, and a test that fails on one attempt
but passes on another (i.e. flaky, not broken) does not fail the build.
Every run writes `build/reports/tests/test/flaky.txt` listing any test whose
attempts disagreed, so instability is visible even though it isn't blocking.
A clean run with no retries writes a "no flaky tests detected" message there.

To run a single test class:

```
./gradlew test --tests "com.mockingbirdbank.service.AccountServiceTest"
```

### Test coverage

```
./gradlew jacocoTestReport              # HTML report at build/reports/jacoco/test/html/index.html
./gradlew jacocoTestCoverageVerification  # enforced thresholds, part of `check`
```

The Vaadin UI packages (`com.mockingbirdbank.ui.*`) are declarative view/layout
code with no branching logic - not meaningfully unit-testable without a full
browser fixture - so coverage is tracked but not gated there. Instead
`jacocoTestCoverageVerification` enforces two rules:

- A 30% project-wide line-coverage floor, so overall coverage can't regress.
- A 90% per-class line-coverage minimum on the business logic packages
  (`model`, `service`, `security`, `config`, `analytics`, `resilience`,
  `observability`), which sit at 92-100% today.

### Interactive QA (browser-driven smoke test)

```
./scripts/run-interactive-qa.sh
```

Unlike `./gradlew test` (which never renders a real page), this drives an
actual Chromium browser against the actually-running app: starts a throwaway
Postgres on port 5433 (so it can't collide with or be confused by whatever a
developer already has on 5432), starts `bootRun` against it, waits for
`/actuator/health`, then uses Playwright (`qa/`, a separate npm project from
the Vaadin-managed root `package.json`) to sign in with the seeded demo login,
confirm the dashboard and an account's transactions render, sign out, and
confirm a wrong password is rejected. Tears down everything it started
(app process + Postgres container) on exit, success or failure.

First run needs `cd qa && npm install && npx playwright install chromium`
(one-time; downloads the Chromium binary Playwright drives).

## Feature flags

`FeatureFlagsProperties` (`src/main/java/com/mockingbirdbank/config/`) binds boolean flags from
`mockingbird.feature-flags.*` in `application.yml`, each defaulting to today's actual behavior.
Override one per-environment with a `MOCKINGBIRD_FEATURE_FLAGS_<NAME>` env var (Spring's relaxed
binding maps it automatically) - no code change or redeploy needed, just a different value for
that variable (e.g. in a k8s manifest or `docker run -e`). `FeatureFlagService` is the typed,
call-site-facing API (`showAccountNumberOnDashboard()`, `showWelcomeBanner()`,
`allowAccountNumberReveal()`); `DashboardView`, `AccountCard`, and `AccountDetailView` use it to
gate small, genuinely optional pieces of UI. `allowAccountNumberReveal` (default on) controls the
eye buttons next to masked account numbers: `AccountNumberDisplay` fetches the full number from
`AccountService.revealAccountNumber` only when clicked (ownership-checked and recorded as an
`account_number.revealed` analytics event, never logged itself), and re-masks after 30 seconds or
on navigation; the dashboard's "Show account numbers" toggle uses `revealAccountNumbers()` to
reveal all of the signed-in holder's accounts in one call. Current values are
visible read-only at `/actuator/featureflags` (`FeatureFlagsEndpoint`) for ops visibility. Add a
new flag by adding one field to `FeatureFlagsProperties` (with a default matching current
behavior) and one accessor on `FeatureFlagService`.

## Product analytics & error insight

`AnalyticsEventService` (`src/main/java/com/mockingbirdbank/analytics/`) is the one call-site API
for "what did a real user actually do or hit" - both are real, aggregable signals today, not
something that only becomes visible by tailing logs after someone complains:

- `track(event, attributes)` for a product event (`dashboard.viewed`, `account.viewed`).
- `trackError(event, reason, attributes)` for a user-facing failure (a 404'd account, a failed
  login), tagged with a short `reason` so it aggregates instead of one series per occurrence.

Every call does two things: increments a Micrometer counter (`product.events`, tagged by
`event`/`outcome`/`reason` - visible at `/actuator/metrics/product.events` once that endpoint is
exposed, and exportable to Prometheus/Datadog/etc. later with zero call-site changes), and writes
one structured line to the `com.mockingbirdbank.analytics.events` logger, which `logback-spring.xml`
routes to its own rolling file (`build/logs/analytics-events.log`, independent of the regular
application log) - something a log shipper could point at today without any code change.

Two things feed it without any call-site code at all:

- `AuthenticationEventListener` listens for the `AuthenticationSuccessEvent` /
  `AbstractAuthenticationFailureEvent` Spring Security already publishes on every login attempt,
  so successful and failed sign-ins are both tracked.
- `GlobalErrorHandler` registers a session-wide Vaadin `ErrorHandler`, so an unhandled exception in
  *any* view - not just ones with their own try/catch - is tracked (`ui.unhandled_exception`,
  tagged by exception type) before falling through to Vaadin's normal error-page/logging behavior.

## Releases

- **Release notes** (`.github/workflows/release-drafter.yml`,
  `.github/release-drafter.yml`) keep a draft GitHub Release up to date on
  every push to `master`, auto-categorizing merged PRs by label (`bug` ->
  Bug Fixes, `enhancement`/`feature` -> Features, `dependencies` ->
  Dependencies, `security` -> Security, `documentation`/`chore` ->
  Maintenance). Nothing is published automatically - a maintainer reviews
  the running draft under the repo's Releases tab.
- **Cutting a release** is one command:
  ```
  git tag v1.2.3
  git push origin v1.2.3
  ```
  `.github/workflows/release.yml` publishes that draft as the real GitHub
  Release for the tag, then builds and pushes a Docker image to GHCR tagged
  both `v1.2.3` and `latest` (reusing the same `Dockerfile` as
  `createLocalDockerImage.zsh`).

## Docker image

```
./createLocalDockerImage.zsh <version>    # build a local image (arm64), e.g. 0.1.0
./runDockerImage.zsh <version>             # run it, reading datasource config from .env
./createDockerHubImage.zsh <version>       # multi-arch build + push to Docker Hub (ajpahl1008/mockingbird-bank)
```

`Dockerfile` is a two-stage build: `./gradlew -Pvaadin.productionMode=true clean
bootJar` in a JDK image, then just the resulting jar copied into a slim JRE
image. `createDockerHubImage.zsh` needs `docker login` done first and pushes
to Docker Hub directly - there's no CI automation for that push yet (see
`.github/workflows/publish-image.yml` for the closest automated equivalent,
which publishes to GHCR on every push to `master` instead).

## Code quality tooling

All of the following are wired into `./gradlew check` (and therefore `build`),
so they run on every CI build and every local commit via the Git hook below.
Run them individually while iterating:

| Tool | Command | Purpose |
|---|---|---|
| Spotless (google-java-format) | `./gradlew spotlessCheck` / `spotlessApply` | Enforces one Java formatting style; `spotlessApply` auto-fixes violations. |
| Checkstyle | `./gradlew checkstyleMain checkstyleTest` | Naming, imports, complexity, file length, TODO tracking. Config: `config/checkstyle/checkstyle.xml`. |
| PMD | `./gradlew pmdMain pmdTest` | Dead/unused code (unused fields, params, locals, private methods). Config: `config/pmd/dead-code.xml`. |
| CPD | `./gradlew cpdCheck` | Copy-paste/duplicate code detection across `src/main/java` and `src/test/java`. |
| JaCoCo | `./gradlew jacocoTestReport` / `jacocoTestCoverageVerification` | Coverage report (`build/reports/jacoco/test/html/index.html`) and enforced thresholds - see [Test coverage](#test-coverage) below. |

A checked-in Git hook (`.githooks/pre-commit`) runs Spotless + Checkstyle before
every commit. It's wired up automatically the first time you run
`./gradlew build` (via the `installGitHooks` task) - no manual `git config`
step needed.

## Security & CI

- **CI** (`.github/workflows/ci.yml`) runs on every push/PR to `master` as two
  parallel jobs rather than one: `fast-checks` (compile plus every
  static-analysis task that doesn't need a database - Spotless, Checkstyle,
  PMD, CPD, dependency-analysis `buildHealth`, AGENTS.md validation, Javadoc)
  reports in well under a minute, while `test` (the full `./gradlew build`,
  including the Testcontainers-backed integration tests) runs at the same
  time rather than after it. A lint mistake shows up fast without waiting
  behind the slower suite.
- **Build performance tracking**: every Gradle invocation writes
  `build/reports/build-performance/summary.txt` - total wall-clock build
  time plus the slowest individual tasks, the same pattern as the per-test
  timing report below - and warns (doesn't fail) if the build takes longer
  than 5 minutes. Both CI jobs upload it as a `build-performance-*` artifact
  so a slow trend is visible across runs.
- **CodeQL** (`.github/workflows/codeql.yml`) runs static security analysis on
  every push/PR plus a weekly schedule; results land in the repo's Security
  tab.
- **Secret scanning**: GitHub's native secret scanning + push protection are
  enabled on this repo, plus `.github/workflows/secret-scan.yml` runs
  [gitleaks](https://github.com/gitleaks/gitleaks) on every push/PR as a
  second, independently-configured check.
- **DAST** (`.github/workflows/dast.yml`) builds a production-mode jar, boots
  it against a real Postgres, and runs an OWASP ZAP baseline scan against it
  on every push to `master` and weekly; the HTML/JSON report is uploaded as a
  workflow artifact for manual triage (it doesn't fail the build - baseline
  findings need a human to separate "noise" from "actual problem").
- **Dependabot** (`.github/dependabot.yml`) covers Gradle, both npm projects
  (root and `qa/`), Docker, and GitHub Actions, weekly, with a cooldown period
  (3-14 days depending on SemVer bump size) so a newly published version has
  time for the community to catch a compromised or broken release before it
  shows up here as a PR.
- **CODEOWNERS** (`.github/CODEOWNERS`) requires review from the repo owner on
  every path.
- **Automated PR review**: `.github/workflows/droid-review.yml` runs Factory
  Droid's automated code review (including a security-focused pass) on every
  non-draft PR; `.github/workflows/droid.yml` lets `@droid` be mentioned in
  an issue/PR comment to invoke it ad hoc. Both need a `FACTORY_API_KEY`
  repository secret and the Factory Droid GitHub App installed to actually
  run.
- **Branch protection** on `master` requires the CI status check to pass,
  blocks force-pushes and branch deletion, and requires conversation
  resolution; repo admins can still bypass it for direct pushes when needed.
- **Log scrubbing**: `logback-spring.xml` routes every log message through
  `ScrubbingMessageConverter`
  (`src/main/java/com/mockingbirdbank/config/logging/`), which redacts
  password/token/API-key-shaped `key=value` pairs, `Authorization: Bearer/Basic`
  headers, and card-number-like digit sequences before a line is written
  anywhere - not just at individual call sites. `DataInitializer` itself no
  longer logs the seeded demo password (see git history if curious what that
  looked like before).

## Project management

- **Issue templates** (`.github/ISSUE_TEMPLATE/`): structured forms for bug
  reports and feature requests (both auto-labeled `triage`), plus a
  `config.yml` that disables freeform blank issues and points a suspected
  security problem at a private GitHub security advisory instead of a public
  issue.
- **PR template** (`.github/pull_request_template.md`): what changed and why,
  how it was verified, and a checklist covering the checks in this file
  (`./gradlew build`, `AGENTS.md` updates, feature-flagging UI-facing work, no
  secrets/PII).
- **Labels**: beyond GitHub's defaults, this repo uses `feature`/`fix`/
  `security`/`chore` (release-drafter categorization), `major`/`minor`/`patch`/
  `skip-changelog` (release-drafter's version resolver), `priority: high`/
  `medium`/`low`, and `area: ui`/`security`/`database`/`ci`/`docs`/`build`/
  `tests` (matching the packages under [Project structure](#project-structure)
  below). The `area: *` ones are applied automatically by
  `.github/workflows/labeler.yml` (config: `.github/labeler.yml`) based on
  which paths a PR touches - not something a human has to remember to set.
- **Backlog health** (`.github/workflows/stale.yml`): a daily job flags any
  issue/PR with no activity in 60 days, then closes it 14 days later if
  nothing changed - `priority: high`, `pinned`, and `security` labelled items
  are exempt, and closing is one click to undo. Keeps an abandoned backlog
  from just accumulating silently.

## Observability & resilience

- **Circuit breakers**: `AccountService`/`TransactionService` wrap their repository calls with a
  named Resilience4j circuit breaker each (`"accounts"`/`"transactions"`,
  `com.mockingbirdbank.resilience.ResilienceConfig`) - 50% failure threshold over a sliding window
  of 10 calls, minimum 10 calls before it can trip, 10s wait before a half-open trial. Once open,
  further calls fail immediately with `ServiceUnavailableException` instead of piling up waiting
  on a slow/down database; `CircuitBreakerBehaviorTest` verifies the real trip/recover behavior
  end-to-end (a repository that genuinely fails every call, not a mocked breaker). Metrics are
  tagged into Micrometer (`resilience4j.circuitbreaker.*`) via `TaggedCircuitBreakerMetrics`.
- **Profiling**: `ProfilingEndpoint` (`com.mockingbirdbank.observability`) exposes a custom
  Actuator endpoint at `/actuator/profiling` (authenticated, see `SecurityConfig`) backed by the
  JDK's own Flight Recorder - `GET` for status, `POST` to start a recording, `DELETE` to stop it
  and write a real `.jfr` file (default `${java.io.tmpdir}/mockingbird-bank-jfr`, override via
  `mockingbird.profiling.output-dir`). No extra agent or profiler dependency needed; open the
  resulting file in VisualVM/JDK Mission Control.
- **Error tracking with context**: `CorrelationIdFilter` puts a correlation ID (reused from an
  incoming `X-Correlation-Id` header, or freshly generated) into SLF4J MDC for every request and
  echoes it back as a response header. `logging.pattern.level` includes it
  (`%X{correlationId:-}`) so every regular log line carries it, and `AnalyticsEventService`
  includes it in its own structured event lines too - so a user-facing error and every log line
  written while handling that same request can be tied together with one ID, not just a
  timestamp.
- **Code quality metrics**: every build writes `build/reports/code-quality/summary.txt` -
  Checkstyle/PMD/CPD violation counts plus project-wide JaCoCo line coverage, tracked over time
  the same way `build/reports/build-performance/summary.txt` is (see below). Each count only
  reflects the *current* build (backed by the same task-duration tracking that build performance
  uses) - a
  count is "not run this build" rather than a stale number left over from a previous, different
  Gradle invocation. CI uploads it as a `code-quality-*` artifact from both jobs (split the same
  way `build-performance-*` is: `fast-checks` only has meaningful lint counts, `test` only has a
  meaningful coverage number, since neither job alone runs everything).
- **Distributed tracing**: `spring-boot-starter-opentelemetry` auto-instruments every incoming
  HTTP request as a span with zero application code (`management.tracing.sampling.probability`,
  default `1.0` - this app is small enough that sampling everything isn't a volume concern).
  Spans export via OTLP to `management.opentelemetry.tracing.export.otlp.endpoint` (default
  `http://localhost:4318/v1/traces` - Spring Boot's own default; no collector there is perfectly
  fine, spans just don't go anywhere). `logging.pattern.level` also includes `traceId`/`spanId`
  from the same MDC mechanism as the correlation ID, so a log line and the trace it happened
  inside of cross-reference in either direction. To verify locally against a real trace backend:
  ```
  docker run -d --name jaeger -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest
  ./gradlew bootRun    # default OTLP endpoint already points at the container above
  # use the app, then:
  curl http://localhost:16686/api/traces?service=mockingbird-bank
  # or open http://localhost:16686 in a browser
  ```
  (The bundled OTLP *metrics* exporter that comes along with this starter is switched off via
  `management.otlp.metrics.export.enabled: false` - only tracing is wanted here, and this app
  already has its own metrics story; see `application.yml` for why.)
- **Deployment observability**: `.github/workflows/publish-image.yml` records a real [GitHub
  Deployment](https://docs.github.com/en/rest/deployments/deployments) for every image it
  publishes, then actually boots that exact image against a throwaway Postgres and waits for
  `/actuator/health` to report healthy before marking the deployment `success` - a bad image is
  visible as a failed deployment even though the build/push step itself already succeeded. See
  the repo's Deployments tab, or `gh api repos/<owner>/<repo>/deployments`.
- **Alerting**: a real Slack incoming webhook (`SLACK_ALERTS_WEBHOOK_URL` repo secret, posting to
  `#mbb-ops`) fires on two failure paths - `ci.yml`'s `notify-on-failure` job when either CI job
  fails on a push to `master` (not on PRs - those failures are already visible directly in the
  PR), and `publish-image.yml`'s smoke-test job when a just-published image fails its
  post-deploy health check. Both degrade to a harmless no-op (logged, not failed) if the secret
  isn't set, so a fork/local run of these workflows doesn't break on a missing secret.
- **Runbooks** (`docs/runbooks/`): concrete, copy-pasteable steps for the failure modes above
  actually surfacing in production - [app won't start](docs/runbooks/app-wont-start.md),
  [database down](docs/runbooks/database-down.md),
  [error spike](docs/runbooks/error-spike.md), and
  [rolling back a bad deploy](docs/runbooks/rollback.md).

## Project structure

```
src/main/java/com/mockingbirdbank/
  Application.java          Spring Boot entry point
  config/                    Startup seeding (DataInitializer)
  model/                     JPA entities (Account, AccountHolder, Transaction, AppUser, ...)
  repository/                Spring Data JPA repositories
  service/                    Application services (AccountService, TransactionService)
  security/                   Spring Security + Vaadin security wiring
  analytics/                    Product-event/error tracking (AnalyticsEventService)
  resilience/                    Circuit breakers around repository calls (ResilienceConfig)
  observability/                 Profiling endpoint, correlation-ID filter
  ui/                          Vaadin views (layout/ and view/)
src/main/resources/
  application.yml              Spring config
  db/migration/                 Flyway migrations (V1__init_schema.sql, ...)
src/test/java/com/mockingbirdbank/  Mirrors the main package layout
config/checkstyle/, config/pmd/     Static analysis rule configs
docs/runbooks/                          Operational runbooks (see Observability & resilience)
k8s/                                    Kubernetes manifests for deployment
```

## Conventions

- Package-by-layer (`model`, `repository`, `service`, `security`, `ui`), not
  package-by-feature - follow the existing layout for new classes.
- Constructors, not `@Autowired` fields, for dependency injection (see any
  `@Service`/`@Component`).
- Lombok `@Getter`/`@Setter`/`@NoArgsConstructor` on entities; avoid hand-writing
  boilerplate accessors.
- Explicit imports only - no wildcard imports (`AvoidStarImport` in Checkstyle).
- New JPA collection associations that might be iterated across multiple parent
  entities (e.g., another `@OneToMany`) should get `@BatchSize` to avoid N+1
  query patterns - see `Account.transactions` and
  `AccountTransactionQueryPerformanceTest` for the pattern and its regression
  guard.

## Known non-issue

`ApplicationSmokeTest.seededUserCanLogIn` deliberately stops at "login
succeeded, session holds the right principal" rather than also asserting a
follow-up `GET /accounts` returns 200. Under MockMvc, Vaadin's Spring servlet
initializes lazily on first real dispatch, which makes an otherwise-correct
authenticated request 403 in that test harness only. The full login -> dashboard
-> logout flow has been verified against a live `bootRun` instance; see the
comment on that test for details before "fixing" it.
