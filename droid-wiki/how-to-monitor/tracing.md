# Tracing

`spring-boot-starter-opentelemetry` creates a span for every incoming HTTP request and exports it over OTLP. No application code touches the tracing API. Traces are only visible if a collector is listening at the configured endpoint, and neither cluster runs one.

## Configuration

| Property | Env var | Default | Set in |
| --- | --- | --- | --- |
| `management.tracing.sampling.probability` | `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `1.0` | `src/main/resources/application.yml` |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | `src/main/resources/application.yml` |
| `management.otlp.metrics.export.enabled` | | `false` | `src/main/resources/application.yml`, see [Metrics](metrics.md) |

The dependency is `org.springframework.boot:spring-boot-starter-opentelemetry` in `build.gradle`. Its comment notes that in Spring Boot 4 tracing moved out of the Actuator starter into this module.

## Per environment

| Environment | Sampling | Endpoint | Result |
| --- | --- | --- | --- |
| `./gradlew bootRun` | `1.0` | `localhost:4318` | Exported if Jaeger is running locally (recipe below), otherwise dropped |
| k3d (`k8s/local/kustomization.yaml`) | `0.0` | not set | Nothing exported. The manifest comment says that without this the exporter logs an ERROR every few seconds. |
| LAB (`k8s/02-app-configmap.yaml`) | not set, so `1.0` | not set, so `localhost:4318` in the pod | Every request is sampled and the export fails, since nothing listens in the pod |
| CI smoke test and DAST | `1.0` | `localhost:4318` | Not collected |

The `application.yml` comment says a missing collector only means the exporter "fails to send spans in the background". The k3d manifest comment reports ERROR log lines in that situation, so expect the same noise in LAB logs unless sampling is turned down there too.

## Local Jaeger

From the "Distributed tracing" section of `AGENTS.md`:

```bash
docker run -d --name jaeger -p 4318:4318 -p 16686:16686 jaegertracing/all-in-one:latest
./gradlew bootRun    # default OTLP endpoint already points at the container
# use the app, then:
curl http://localhost:16686/api/traces?service=mockingbird-bank
# or open http://localhost:16686
```

The service name is `mockingbird-bank`, from `spring.application.name`. Remove the container with `docker rm -f jaeger` when done.

To trace a k3d deploy, add both env vars to the `configMapGenerator` in `k8s/local/kustomization.yaml`: set the sampling probability to `1.0` and the endpoint to a collector the pod can reach, such as a Jaeger deployment in the same namespace. Then redeploy with `./scripts/k3d-deploy.sh` (see [Local k3d](../deployment/local-k3d.md)).

## What a trace contains

Expect one server span per HTTP request. `build.gradle` declares no JDBC, JPA or Resilience4j tracing instrumentation, so database queries and circuit breaker decisions do not appear as child spans. `docs/runbooks/error-spike.md` suggests using the trace to see "which downstream call (database query, circuit breaker trip)" a request spent time on; with the current dependencies that detail is not in the trace.

Vaadin Flow sends each UI interaction as its own request, so one click is one trace, and a user session is many unrelated traces.

## Trace IDs and correlation IDs

Both IDs end up in MDC and in the log bracket `[correlationId,traceId,spanId]` set by `logging.pattern.level`.

| | Correlation ID | Trace ID |
| --- | --- | --- |
| Set by | `src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` | Micrometer Tracing via the OpenTelemetry bridge |
| Source | Incoming `X-Correlation-Id` header, otherwise a random UUID | New per request, or continued from incoming trace context headers |
| Returned to the caller | Yes, `X-Correlation-Id` response header | No |
| In analytics event lines | Yes, `correlationId=` | No |
| In the console log | Yes | Yes, while a span is active |
| Depends on sampling or a collector | No | Export does; see [Logging](logging.md#the-console-log-line) for the IDs in logs |

The correlation ID is not added to spans as an attribute. To go from a user report to a trace:

1. Get the `X-Correlation-Id` from the user's browser dev tools, or find it on an `outcome=error` analytics line.
2. Grep the console log for that ID. The same lines carry the `traceId`.
3. Look up the `traceId` in Jaeger or whichever backend receives the spans.

## Key source files

| File | Purpose |
| --- | --- |
| `build.gradle` | `spring-boot-starter-opentelemetry` dependency |
| `src/main/resources/application.yml` | Sampling, OTLP endpoint, log pattern |
| `k8s/local/kustomization.yaml` | Sampling set to `0.0` for k3d |
| `k8s/02-app-configmap.yaml` | LAB environment, no tracing overrides |
| `src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` | The other request ID |

[Observability](../systems/observability.md) covers the code side.
