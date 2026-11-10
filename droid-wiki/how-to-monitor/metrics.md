# Metrics

The app records its own counters and gauges in Micrometer, but nothing can read them over HTTP today. `metrics` is missing from the Actuator exposure list, and no external registry such as Prometheus is configured, so the numbers live only in the JVM's memory and reset on every restart. This page lists what is recorded and how to make it readable.

## Current state

`src/main/resources/application.yml` sets:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,featureflags,profiling
```

Consequences:

- `/actuator/metrics` and `/actuator/metrics/<name>` are not mapped. A request gets a 404, or is denied first by Spring Security, because `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` only opens `/actuator/health/**` and `/actuator/info` and only authenticates `/actuator/featureflags` and `/actuator/profiling`. `VaadinSecurityConfigurer` denies anything else.
- `management.otlp.metrics.export.enabled` is `false`, so the OTLP metrics registry that `spring-boot-starter-opentelemetry` brings along does not push anywhere. The comment in `application.yml` explains why: Jaeger does not accept OTLP metrics, and the exporter logged a warning every minute.
- With no exporting registry active, Spring Boot falls back to an in-memory registry.

Several documents assume the endpoint works: the `AnalyticsEventService` Javadoc, the "Product analytics" section of `AGENTS.md`, the metrics comment in `application.yml`, the `ResilienceConfig` comment, and the `curl .../actuator/metrics/product.events` steps in `docs/runbooks/README.md` and `docs/runbooks/error-spike.md`. Until one of the options below is applied, use the analytics log instead ([Logging](logging.md#grep-recipes)), which carries the same events.

## What is recorded

### product.events

One counter, incremented by every `AnalyticsEventService.track` and `trackError` call (`src/main/java/com/mockingbirdbank/analytics/AnalyticsEventService.java`).

| Tag | Values |
| --- | --- |
| `event` | `dashboard.viewed`, `account.viewed`, `account_number.revealed`, `user.login`, `ui.unhandled_exception` |
| `outcome` | `success` or `error` |
| `reason` | `n/a` for successes; for errors `not_found`, `forbidden`, or an exception's simple class name (for example `BadCredentialsException`) |

Attributes such as `accountId` and `username` are not tags; they only go to the log line, so the number of series stays small. Which call site emits which combination is in the [event catalog](../systems/analytics.md#event-catalog).

`ui.unhandled_exception` and `user.login` failures use exception class names as the reason, so the reason set grows with every new exception type that reaches those paths.

### resilience4j.circuitbreaker.*

`src/main/java/com/mockingbirdbank/resilience/ResilienceConfig.java` binds the circuit breaker registry with `TaggedCircuitBreakerMetrics`. Every meter is tagged `name=accounts`, `name=transactions` or `name=programs`.

| Meter | Use it for |
| --- | --- |
| `resilience4j.circuitbreaker.state` | Which state each breaker is in; the gauge for the current `state` tag reads 1 |
| `resilience4j.circuitbreaker.not.permitted.calls` | Calls rejected while open, each one a `ServiceUnavailableException` |
| `resilience4j.circuitbreaker.failure.rate` | Failure rate over the 10-call window |
| `resilience4j.circuitbreaker.calls` | Call timings by `kind` (successful, failed, ignored) |
| `resilience4j.circuitbreaker.buffered.calls` | Calls currently in the sliding window |

The thresholds behind these numbers are on the [Resilience](../systems/resilience.md#configuration) page.

### JVM, HTTP and pool metrics

`spring-boot-starter-actuator` registers Micrometer's standard binders without any code here. Expect names such as `jvm.memory.used`, `jvm.gc.pause`, `jvm.threads.live`, `process.cpu.usage`, `http.server.requests` and `hikaricp.connections.active`. Vaadin sends every UI round trip as an HTTP request to the same servlet, so `http.server.requests` is dominated by those round trips and says little about individual views; `product.events` is the per-view signal.

## Option 1: expose the metrics endpoint

The smallest change, suitable for ad hoc checks during an incident.

1. Add `metrics` to the exposure list in `src/main/resources/application.yml`:

   ```yaml
   include: health,info,featureflags,profiling,metrics
   ```

   Or set `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,featureflags,profiling,metrics` in the environment for one deployment. The env var replaces the list, so repeat every existing entry.
2. Secure it in `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` by adding `"/actuator/metrics/**"` to the `.authenticated()` matcher next to `/actuator/featureflags` and `/actuator/profiling`. Without a matcher the Vaadin security configuration denies the request. Do not add it to `permitAll`; meter tags include exception class names and request URIs.
3. Query it from a session that is signed in, for example by copying the `JSESSIONID` cookie from a browser:

   ```bash
   H=http://<host>
   C='JSESSIONID=<cookie>'
   curl -s -b "$C" "$H/actuator/metrics" | jq '.names'
   curl -s -b "$C" "$H/actuator/metrics/product.events?tag=outcome:error" | jq
   curl -s -b "$C" "$H/actuator/metrics/product.events?tag=outcome:error&tag=event:user.login" | jq
   curl -s -b "$C" "$H/actuator/metrics/resilience4j.circuitbreaker.state?tag=name:accounts&tag=state:open" | jq
   curl -s -b "$C" "$H/actuator/metrics/resilience4j.circuitbreaker.not.permitted.calls" | jq
   ```

The event name for logins is `user.login`. `docs/runbooks/error-spike.md` uses `tag=event:login`, which matches nothing.

The values are cumulative since the pod started and are lost on restart, so this is good for "is it happening now", not for trends.

## Option 2: add a scraping or push registry

For history and dashboards, add a Micrometer registry. Call sites do not change.

- **Prometheus**: add `runtimeOnly 'io.micrometer:micrometer-registry-prometheus'` to `build.gradle`, add `prometheus` to the exposure list, and decide how the scraper authenticates. A scraper cannot sign in through the Vaadin login view, so either serve Actuator on a separate port with `management.server.port` that the Kubernetes Service does not publish and permit the path there, or permit `/actuator/prometheus` and restrict it at the network level. Check the Spring Boot 4 reference for whether an extra Boot module is needed alongside the registry, since Boot 4 split Actuator into modules.
- **OTLP**: set `management.otlp.metrics.export.enabled` to `true` and point it at a collector that accepts OTLP metrics, such as the OpenTelemetry Collector. This reuses the registry that is already on the classpath.

Either way, run `./gradlew buildHealth` afterwards; the dependency-analysis plugin checks how dependencies are declared.

## Build metrics are not runtime metrics

Some files under `build/reports/` look like metrics but describe the build, not the running app:

| File | Contents | Where it is kept |
| --- | --- | --- |
| `build/reports/build-performance/summary.txt` | Total build time and slowest tasks; warns over 5 minutes | CI artifacts `build-performance-fast-checks` and `build-performance-test`, 14 days |
| `build/reports/code-quality/summary.txt` | Checkstyle, PMD and CPD counts plus JaCoCo line coverage for the current build | CI artifacts `code-quality-fast-checks` and `code-quality-test`, 14 days |
| `build/reports/tests/test/timings.txt` | Per-test durations, slowest first | Inside the `test-report` artifact |
| `build/reports/tests/test/flaky.txt` | Tests whose retry attempts disagreed | Inside the `test-report` artifact |

All are generated by `build.gradle` and uploaded by `.github/workflows/ci.yml`. [Tooling](../how-to-contribute/tooling.md) covers them.

## Key source files

| File | Purpose |
| --- | --- |
| `src/main/resources/application.yml` | Endpoint exposure, OTLP metrics switch |
| `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` | Which Actuator paths are reachable |
| `src/main/java/com/mockingbirdbank/analytics/AnalyticsEventService.java` | `product.events` counter |
| `src/main/java/com/mockingbirdbank/resilience/ResilienceConfig.java` | Breaker meter binding |
| `build.gradle` | `micrometer-core`, `resilience4j-micrometer`, `spring-boot-starter-opentelemetry` |
