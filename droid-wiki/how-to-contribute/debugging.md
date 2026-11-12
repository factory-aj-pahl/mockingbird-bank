# Debugging

When something breaks locally or in a deployed instance, there are three places to look: the console log, the separate analytics event log, and the Actuator endpoints. This page explains how to read each one and lists the errors contributors hit most often. Production incidents are covered by the runbooks in `docs/runbooks/`.

## Logs

### Application log

Logs go to the console only, using Spring Boot's default console appender with one change to the level column, set in `src/main/resources/application.yml`:

```
logging.pattern.level: '%5p [%X{correlationId:-},%X{traceId:-},%X{spanId:-}]'
```

So a line written while handling a request looks like `INFO [3f2c...,a1b2...,c3d4...] ...`. Lines written outside a request (startup, scheduled work) show empty slots `[,,]`. The startup line `Mockingbird Bank <version> started (commit ..., branch ...)` from `BuildVersion` tells you which build you are looking at.

Every message passes through `src/main/java/com/mockingbirdbank/config/logging/ScrubbingMessageConverter.java` before it is written, which redacts password, secret, token and API-key `key=value` pairs, `Authorization: Bearer/Basic` headers and 13 to 19 digit card-like numbers. If a value you expected to see shows as redacted, that is why.

### Analytics event log

`src/main/resources/logback-spring.xml` routes the `com.mockingbirdbank.analytics.events` logger to `build/logs/analytics-events.log`, relative to the process working directory. It rolls daily and keeps 30 days. Each line is one `track` or `trackError` call written as space-separated `key=value` pairs: `event`, `outcome`, `reason` (errors only), `correlationId` (when inside a request), then the call's attributes such as `accountId`. The logger is additive, so the same lines also reach the console.

Useful events when debugging:

| Event | Meaning |
| --- | --- |
| `user.login` with `outcome=error` | Failed sign-in, `reason` is the Spring Security exception simple name |
| `account.viewed` with `reason=not_found` | `/accounts/{id}` for an ID that does not exist, belongs to another holder, or is not a number that fits a `long` (then with no `accountId` attribute) |
| `account_number.revealed` with `reason=forbidden` | Reveal attempted for an account the user does not own |
| `ui.unhandled_exception` | Any exception that escaped a view, `reason` is the exception type |
| `programs.unavailable` | A program section showed "Programs can't be shown right now"; `reason` is `ServiceUnavailableException` for an open `programs` breaker, otherwise the database exception type, which `ProgramLookup` also logs at WARN |

See [Analytics](../systems/analytics.md) and [Logging](../how-to-monitor/logging.md).

### Following one request

`src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` reuses an incoming `X-Correlation-Id` header or generates a UUID, and echoes it back on the response. A quick check that the header comes back:

```bash
curl -s -H 'X-Correlation-Id: debug-123' -o /dev/null -D - localhost:8080/actuator/health | grep -i x-correlation-id
```

That only shows header propagation. `/actuator/health` does not call `AnalyticsEventService`, so `debug-123` never appears in `build/logs/analytics-events.log`.

To trace a request that does write an analytics event, use the app in the browser. Signing in, opening the dashboard or opening an account each write one. Vaadin UI interactions after the first page load arrive as XHR requests to the same servlet, so each click gets its own ID. Copy the `X-Correlation-Id` response header of that request from the browser dev tools, then grep for it:

```bash
grep '<correlation-id>' build/logs/analytics-events.log
```

Search the console output of `bootRun` for the same ID to find the regular log lines from that request.

## Actuator endpoints

| Endpoint | Access | Use |
| --- | --- | --- |
| `/actuator/health` | Public | `UP`/`DOWN`, including the `db` component |
| `/actuator/info` | Public | Build version, commit and branch only |
| `/actuator/featureflags` | Signed-in session | Current feature flag values |
| `/actuator/profiling` | Signed-in session | Start, stop and query a JFR recording |

`/actuator/metrics` is not exposed (`management.endpoints.web.exposure.include` is `health,info,featureflags,profiling`), even though `AGENTS.md` and `docs/runbooks/error-spike.md` refer to `/actuator/metrics/product.events`. To inspect counters locally, add `metrics` to that list for your run, for example with `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,featureflags,profiling,metrics`. Exposure alone is not enough: `VaadinSecurityConfigurer` denies any path that `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` does not match explicitly, so you also need a local, uncommitted matcher for `/actuator/metrics/**` next to the `featureflags` and `profiling` rule. See [API](../api.md).

## Profiling with JFR

For a slow window you can reproduce, start a Flight Recorder recording, reproduce, then stop it:

1. `POST /actuator/profiling` starts a recording with JFR's `default` settings and returns the target file path.
2. Reproduce the slow behavior.
3. `DELETE /actuator/profiling` stops it and writes `mockingbird-bank-<epoch millis>.jfr` to `mockingbird.profiling.output-dir` (default `${java.io.tmpdir}/mockingbird-bank-jfr`).
4. Open the file in JDK Mission Control or VisualVM.

Both calls need an authenticated session and a valid Spring Security CSRF token; the session cookie alone is not enough. See [Calling the authenticated endpoints](../api.md#calling-the-authenticated-endpoints). "Already running" and "nothing running" come back as a 200 response with an `error` field, so read the body rather than the status code. Endpoint details are in [Observability](../systems/observability.md#profiling-with-jfr).

## Tracing

Every HTTP request becomes an OpenTelemetry span exported to `http://localhost:4318/v1/traces` by default. With no collector there, export fails quietly in the background. To see traces locally, run Jaeger as described in `AGENTS.md` under "Distributed tracing", then match the `traceId` in a log line to the trace in Jaeger. See [Tracing](../how-to-monitor/tracing.md).

## Common errors

| Symptom | Cause | Fix |
| --- | --- | --- |
| `Connection to localhost:5432 refused` on `bootRun` | No Postgres running | `docker compose up -d`, or use `./scripts/dev-up.sh`. For another database set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| `Schema validation: missing table [...]` at startup | `spring.flyway.baseline-on-migrate: true` marked V1 as applied on a non-empty database that already had other tables, so the app tables were never created. This happened on the first LAB database | Use a database dedicated to the app (see `scripts/bootstrap-lab-db.sql`), or drop the local volume with `docker compose down -v`. Check `flyway_schema_history` |
| `FlywayException: Validate failed`, checksum mismatch | An already-applied migration in `src/main/resources/db/migration/` was edited | Never edit an applied migration; add the next version (`V3__...`). On a throwaway database, `docker compose down -v` |
| Demo login fails on an existing database | `DataInitializer` seeds only an empty database, so a changed `MOCKINGBIRD_DEV_PASSWORD` has no effect later | `docker compose down -v` and restart |
| A new view returns 403 or redirects to login although signed in | The view, or its parent layout, lacks an access annotation. Vaadin treats an unannotated layout as `@DenyAll` | Annotate the view (`@PermitAll`) and use `MainLayout`, which carries `@PermitAll` for this reason. See [UI](../systems/ui.md) |
| `ApplicationSmokeTest` 403 after login | Expected under MockMvc, Vaadin's servlet initializes lazily | Leave it; see [Testing](testing.md#the-login-smoke-test-stops-early-on-purpose) |
| Testcontainers: `Could not find a valid Docker environment` | Docker not running | Start Docker; the integration tests cannot run without it |
| Testcontainers times out inside the dev container | Sibling containers on the default bridge network are unreachable from the compose network | `.devcontainer/docker-compose.yml` sets `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`; keep it if you change that file, and set it yourself in similar Docker-outside-of-Docker setups |
| `installGitHooks` fails with "not in a git directory" in the dev container | Git refuses a bind-mounted repo owned by a different UID ("dubious ownership") | `postCreateCommand` in `.devcontainer/devcontainer.json` runs `git config --global --add safe.directory /workspace`; rerun it if needed |
| Gradle cannot find a Java 21 toolchain | `build.gradle` requires `JavaLanguageVersion.of(21)` | Install a JDK 21 (sdkman, Homebrew, Corretto) and check with `./gradlew -version`, or use the dev container |
| Odd frontend errors after a Vaadin upgrade or branch switch | Stale generated frontend files. The root `package.json`, `vite.config.ts`, `node_modules/` and `src/main/frontend/generated/` are generated and git-ignored | Delete those generated files (or run the Vaadin plugin's `vaadinClean` task) and rebuild. Never hand-edit the root `package.json`; the Playwright project in `qa/` has its own |
| `Port 8080 was already in use` | An old `bootRun` or the QA script's app is still running | Stop it, or set `SERVER_PORT` |
| `Vaadin is running in DEVELOPMENT mode` in a container | Image built without `-Pvaadin.productionMode=true` | Rebuild with the `Dockerfile`, which passes it |

## Runbooks

For a deployed instance, start from the runbooks:

- `docs/runbooks/app-wont-start.md`: crash loops and startup failures, with a table of log signatures.
- `docs/runbooks/database-down.md`: health `DOWN`, circuit breaker behavior during the outage, recovery.
- `docs/runbooks/error-spike.md`: quantifying and tracing a spike in tracked errors. Note it relies on `/actuator/metrics`, which is not exposed, and uses `event:login` where the event is named `user.login`.
- `docs/runbooks/rollback.md`: rolling back the Kubernetes Deployment and reverting the commit.

See also [Pitfalls](../background/pitfalls.md) and [How to monitor](../how-to-monitor/index.md).
