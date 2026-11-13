# Logging

The app writes two log streams: the regular application log on stdout, and a dedicated analytics event log that is also copied to stdout. Every message in both passes through a scrubbing converter first. This page covers reading them; [Analytics](../systems/analytics.md) and [Observability](../systems/observability.md) cover the code that writes them.

## Configuration at a glance

| Setting | Value | File |
| --- | --- | --- |
| Root level | `INFO`, to the `CONSOLE` appender | `src/main/resources/logback-spring.xml` |
| Level column | `%5p [%X{correlationId:-},%X{traceId:-},%X{spanId:-}]` | `src/main/resources/application.yml` (`logging.pattern.level`) |
| `%msg` / `%m` | Replaced by `ScrubbingMessageConverter` | `src/main/resources/logback-spring.xml` |
| Analytics logger | `com.mockingbirdbank.analytics.events`, `INFO`, `additivity="true"` | `src/main/resources/logback-spring.xml` |
| Analytics file | `build/logs/analytics-events.log`, daily rollover, 30 days kept | `src/main/resources/logback-spring.xml` |

There is no file appender for the application log. In Kubernetes, stdout is the only place it goes.

## The console log line

`logback-spring.xml` includes Spring Boot's own `defaults.xml` and `console-appender.xml`, so the line layout is Spring Boot's default. The app only changes the level column through `logging.pattern.level`, which adds one bracket right after the level:

```
<timestamp>  INFO [<correlationId>,<traceId>,<spanId>] <pid> --- [<app>] [<thread>] <logger> : <message>
```

An illustrative line from a dashboard request (IDs shortened):

```
2026-11-04T10:15:32.118-05:00  INFO [3f2a9c1e-...,4bf92f3577b34da6...,00f067aa0ba902b7] 1 --- [mockingbird-bank] [nio-8080-exec-3] c.mockingbirdbank.analytics.events : event=dashboard.viewed outcome=success correlationId=3f2a9c1e-... accountCount=3
```

- `correlationId` is set by `src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` for the duration of each HTTP request and echoed back in the `X-Correlation-Id` response header.
- `traceId` and `spanId` are filled in by Micrometer Tracing while a traced operation is active. With sampling at `0.0` (the k3d setting) the span is not exported, and the IDs may be present or empty depending on how the tracer handles unsampled spans. See [Tracing](tracing.md).
- Lines written outside a request, such as startup, show `[,,]`.
- Spring Boot may also print its own trace correlation block further along the line when tracing is on the classpath (`logging.pattern.correlation`). The bracket after the level is the one this repo configures.

The exact layout depends on the Spring Boot version's default pattern. Check a real line from `kubectl logs` before writing a parser against it.

## Scrubbing

`src/main/java/com/mockingbirdbank/config/logging/ScrubbingMessageConverter.java` rewrites the formatted message before any appender writes it. It applies three patterns in order:

| Pattern | Example input | Output |
| --- | --- | --- |
| `Authorization: Bearer` or `Basic` header | `Authorization: Bearer abc.def` | `Authorization: Bearer ***REDACTED***` |
| Secret-shaped `key=value` or `key: value` (`password`, `passwd`, `pwd`, `secret`, `token`, `api_key`/`api-key`/`apikey`, `access_token`, `refresh_key`, `refresh_token`) | `password=hunter2` | `password=***REDACTED***` |
| 13 to 19 digits, optionally separated by single spaces or dashes | `4111 1111 1111 1111` | `***REDACTED***` |

Things to know when reading scrubbed logs:

- It only touches the message (`%msg`). Logger names, thread names and MDC values such as a caller-supplied `X-Correlation-Id` are not scrubbed.
- Full account numbers in this app are 13 digits, so they are redacted if they ever reach a log message.
- Any 13-digit number is redacted, including epoch milliseconds. An odd `***REDACTED***` in a timestamp-looking spot is probably that.
- Stack traces are printed by Logback's throwable converter, not `%msg`, so exception messages inside a stack trace are not scrubbed.

## Analytics event log

`AnalyticsEventService` writes one line per `track` or `trackError` call to the `com.mockingbirdbank.analytics.events` logger. The full event list is in [Analytics](../systems/analytics.md#event-catalog).

### Location

| Run mode | File |
| --- | --- |
| `./gradlew bootRun` from the repo root | `build/logs/analytics-events.log` in the repo. `./gradlew clean` deletes it. |
| Docker image (`Dockerfile`, `WORKDIR /app`) | `/app/build/logs/analytics-events.log` inside the container |
| LAB and k3d pods | Same path in the pod. No volume is mounted, so it is gone after a restart or redeploy. |

Because of `additivity="true"`, every event also appears in the console log, so `kubectl logs` is the easier copy to reach in a cluster until a log shipper is added.

### Rotation

`TimeBasedRollingPolicy` rolls the file at midnight to `build/logs/analytics-events.<yyyy-MM-dd>.log.gz` and keeps 30 days (`maxHistory`). There is no size cap.

### Format

```
<ISO-8601 timestamp> event=<name> outcome=<success|error> [reason=<reason>] [correlationId=<id>] <attribute>=<value> ...
```

For example, a denied reveal:

```
2026-11-04T10:15:32.118-05:00 event=account_number.revealed outcome=error reason=forbidden correlationId=3f2a9c1e-... accountId=42
```

`reason` appears only for errors. Values are not quoted or escaped. `user.login` lines include the attempted `username`, including on failed attempts, and that is kept for the 30-day retention window.

## Grep recipes

All of these work on the file locally. In a pod, prefix them with `kubectl -n mockingbird-bank exec deploy/mockingbird-app --` and use the `/app/build/logs/...` path, or pipe `kubectl logs` into the same `grep`.

```bash
F=build/logs/analytics-events.log

# All user-facing errors
grep 'outcome=error' "$F"

# Error counts by event and reason
grep -o 'event=[^ ]* outcome=error reason=[^ ]*' "$F" | sort | uniq -c | sort -rn

# Failed logins, with the attempted username
grep 'event=user.login outcome=error' "$F"

# Unhandled Vaadin exceptions by exception type
grep 'event=ui.unhandled_exception' "$F" | grep -o 'reason=[^ ]*' | sort | uniq -c

# Older days (rotated and gzipped)
zgrep 'outcome=error' build/logs/analytics-events.2026-11-0*.log.gz

# Events from a cluster without exec, using the console copy
kubectl -n mockingbird-bank logs deploy/mockingbird-app --since=1h | grep 'outcome=error'
```

To follow one request across both streams, take the `correlationId=` value from an event line and search the console log for the bare ID:

```bash
kubectl -n mockingbird-bank logs deploy/mockingbird-app | grep '<correlation-id>'
```

In the console log the ID sits inside the `[...]` bracket, not as `correlationId=<id>`, so `grep 'correlationId=<id>'` only finds analytics lines. Vaadin sends each server round trip as its own HTTP request, so a correlation ID covers one round trip, not a whole user session.

## Startup version line

`src/main/java/com/mockingbirdbank/observability/BuildVersion.java` logs one line on `ApplicationReadyEvent`:

```
Mockingbird Bank 0.3.0-78a2f03 started (commit 78a2f03, branch master, built 2026-11-13T13:20:04Z)
```

The `built ...` part appears only for image builds, which pass `-PbuildTime`. An image built without the metadata build args reports `unknown` for commit and branch. Find it with:

```bash
kubectl -n mockingbird-bank logs deploy/mockingbird-app | grep 'Mockingbird Bank .* started'
```

See [Build version](../features/build-version.md) for where the values come from.

## kubectl commands

| Task | LAB (`mockingbird-bank`) | k3d (`mockingbird-bank-dev`) |
| --- | --- | --- |
| Tail the app | `kubectl -n mockingbird-bank logs deploy/mockingbird-app --tail=200 -f` | `./scripts/k3d-deploy.sh logs` |
| Previous crashed container | `kubectl -n mockingbird-bank logs deploy/mockingbird-app --previous` | `kubectl --context k3d-dev-k8s -n mockingbird-bank-dev logs deploy/mockingbird-app --previous` |
| Postgres | External server, not in the cluster | `./scripts/k3d-deploy.sh logs postgres` |
| Pod events and probe failures | `kubectl -n mockingbird-bank describe pod -l app=mockingbird-app` | `kubectl --context k3d-dev-k8s -n mockingbird-bank-dev describe pod -l app=mockingbird-app` |
| Read the analytics file | `kubectl -n mockingbird-bank exec deploy/mockingbird-app -- tail -n 100 /app/build/logs/analytics-events.log` | Same with `--context k3d-dev-k8s -n mockingbird-bank-dev` |

`docs/runbooks/app-wont-start.md` explains why `--previous` matters after a crash loop.

## Adding a log statement

1. Declare a logger the way `BuildVersion` and `DataInitializer` do: `private static final Logger log = LoggerFactory.getLogger(MyClass.class);`.
2. Use SLF4J placeholders (`log.info("Loaded {} accounts", count)`), not string concatenation.
3. Do not log passwords, full account numbers, emails or full names. The scrubber is a safety net and does not know about names or emails. `DataInitializer` used to log the seeded password, which is why the scrubber exists.
4. If the line is really a product event or a user-facing failure that someone will want to count, call `AnalyticsEventService.track` or `trackError` instead. That gives you a counter as well as a line.
5. Log on the request thread when possible, so the line picks up the correlation ID from MDC. Work moved to another thread loses it.
6. Stay at `INFO` or above for anything you need in production. Root is `INFO` and no per-package level is configured, so `DEBUG` lines will not show up unless you add `logging.level.<package>=DEBUG` (or the matching `LOGGING_LEVEL_<PACKAGE>` env var).

[Debugging](../how-to-contribute/debugging.md) covers using these logs while developing.

## Key source files

| File | Purpose |
| --- | --- |
| `src/main/resources/logback-spring.xml` | Console appender, scrubbing converter, analytics file appender |
| `src/main/resources/application.yml` | `logging.pattern.level` with the MDC bracket |
| `src/main/java/com/mockingbirdbank/config/logging/ScrubbingMessageConverter.java` | Redaction patterns |
| `src/main/java/com/mockingbirdbank/analytics/AnalyticsEventService.java` | Event line format |
| `src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` | Correlation ID in MDC |
| `src/main/java/com/mockingbirdbank/observability/BuildVersion.java` | Startup version line |
