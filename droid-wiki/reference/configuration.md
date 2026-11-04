# Configuration

Every setting that changes how Mockingbird Bank builds, starts or deploys, with its default and where it is defined. Runtime settings come from `src/main/resources/application.yml`, so any of them can be overridden with Spring Boot's relaxed-binding env var (for example `spring.jpa.open-in-view` becomes `SPRING_JPA_OPEN_IN_VIEW`). The tables below name an env var explicitly only where the YAML uses a `${VAR:default}` placeholder or the code reads one directly.

Values bind at startup. Changing an env var, including a feature flag, needs a restart or a new pod.

## Application properties

All rows below come from `src/main/resources/application.yml` unless noted.

### Datasource, JPA and Flyway

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `spring.application.name` | (relaxed) | `mockingbird-bank` | Service name, also used as the OpenTelemetry service name in traces |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/mydatabase` | JDBC URL of the PostgreSQL database |
| `spring.datasource.username` | `SPRING_DATASOURCE_USERNAME` | `admin` | Database user |
| `spring.datasource.password` | `SPRING_DATASOURCE_PASSWORD` | `secret` | Database password (local fallback only) |
| `spring.jpa.hibernate.ddl-auto` | (relaxed) | `validate` | Hibernate checks entity mappings against the Flyway-created schema and fails startup on a mismatch; it never alters tables |
| `spring.jpa.open-in-view` | (relaxed) | `false` | The Hibernate session closes when the service method returns; views receive detached entities |
| `spring.jpa.properties.hibernate.format_sql` | (relaxed) | `true` | Pretty-prints SQL when SQL logging is turned on |
| `spring.flyway.baseline-on-migrate` | (relaxed) | `true` | On a non-empty schema with no `flyway_schema_history`, marks V1 as applied instead of running it |

See [Persistence](../systems/persistence.md) for why `validate` and `baseline-on-migrate` are set, and [Pitfalls](../background/pitfalls.md) for the LAB database that baseline-on-migrate skipped.

### Server, logging and Vaadin

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `server.port` | `SERVER_PORT` | `8080` | HTTP port |
| `logging.pattern.level` | (relaxed) | `%5p [%X{correlationId:-},%X{traceId:-},%X{spanId:-}]` | Adds the correlation ID, trace ID and span ID from MDC next to the log level on every line |
| `vaadin.launch-browser` | `VAADIN_LAUNCH_BROWSER` (relaxed) | `true` | Opens a browser tab when the app starts in development mode; `k8s/local/kustomization.yaml` sets it to `false` |

Logging output itself is configured in `src/main/resources/logback-spring.xml`, which is not property-driven:

| Setting | Value | Effect |
| --- | --- | --- |
| `%msg` / `%m` converter | `com.mockingbirdbank.config.logging.ScrubbingMessageConverter` | Redacts secrets and card-like numbers in every log line |
| `ANALYTICS_EVENTS` appender file | `build/logs/analytics-events.log` | Separate file for logger `com.mockingbirdbank.analytics.events` |
| Rollover pattern | `build/logs/analytics-events.%d{yyyy-MM-dd}.log.gz` | Daily rollover, gzipped |
| `maxHistory` | `30` | Keeps 30 days of analytics logs |
| Root level | `INFO`, console appender | Regular application log goes to stdout |

See [Logging](../how-to-monitor/logging.md).

### Actuator, info and tracing

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `management.endpoints.web.exposure.include` | (relaxed) | `health,info,featureflags,profiling` | The only Actuator endpoints reachable over HTTP. `metrics` is not in the list |
| `management.info.defaults.enabled` | (relaxed) | `false` | Turns every `/actuator/info` contributor off |
| `management.info.build.enabled` | (relaxed) | `true` | Re-enables the `build` section (version, build time) |
| `management.info.git.enabled` | (relaxed) | `true` | Re-enables the `git` section |
| `management.info.git.mode` | (relaxed) | `simple` | Short commit, branch and commit time only |
| `management.tracing.sampling.probability` | `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `1.0` | Fraction of requests traced. `k8s/local/kustomization.yaml` sets `0.0` because no collector runs in k3d |
| `management.opentelemetry.tracing.export.otlp.endpoint` | `MANAGEMENT_OPENTELEMETRY_TRACING_EXPORT_OTLP_ENDPOINT` | `http://localhost:4318/v1/traces` | OTLP/HTTP span export target. Nothing listening there is harmless |
| `management.otlp.metrics.export.enabled` | (relaxed) | `false` | Disables the OTLP metrics exporter that `spring-boot-starter-opentelemetry` brings in |
| `management.endpoint.health.probes.enabled` | `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` | not set (Spring Boot default) | Set to `true` in `k8s/local/kustomization.yaml` so `/actuator/health/liveness` exists for the k3d probes |

Endpoint access rules (which endpoints are public) are in `src/main/java/com/mockingbirdbank/security/SecurityConfig.java`; see [API](../api.md) and [Tracing](../how-to-monitor/tracing.md).

### Feature flags

Bound by `src/main/java/com/mockingbirdbank/config/FeatureFlagsProperties.java` from `mockingbird.feature-flags.*`. Defaults are declared twice, once in the Java field initializers and once in `src/main/resources/application.yml`; both agree today.

| Property | Env var | Default | Effect |
| --- | --- | --- | --- |
| `mockingbird.feature-flags.show-account-number-on-dashboard` | `MOCKINGBIRD_FEATURE_FLAGS_SHOW_ACCOUNT_NUMBER_ON_DASHBOARD` | `true` | Account cards show the masked number |
| `mockingbird.feature-flags.show-welcome-banner` | `MOCKINGBIRD_FEATURE_FLAGS_SHOW_WELCOME_BANNER` | `false` | Dashboard shows a welcome banner |
| `mockingbird.feature-flags.allow-account-number-reveal` | `MOCKINGBIRD_FEATURE_FLAGS_ALLOW_ACCOUNT_NUMBER_REVEAL` | `true` | Eye buttons and the "Show account numbers" toggle |
| `mockingbird.feature-flags.show-build-version` | `MOCKINGBIRD_FEATURE_FLAGS_SHOW_BUILD_VERSION` | `true` | Version label in the top bar |

Each word needs its own underscore in the env var name. Current values are readable at `/actuator/featureflags` after sign-in. Details are in [Feature flags](../features/feature-flags.md).

### Profiling

| Property | Env var | Default | Source | Effect |
| --- | --- | --- | --- | --- |
| `mockingbird.profiling.output-dir` | `MOCKINGBIRD_PROFILING_OUTPUT_DIR` (relaxed) | `${java.io.tmpdir}/mockingbird-bank-jfr` | `@Value` in `src/main/java/com/mockingbirdbank/observability/ProfilingEndpoint.java` | Directory for `.jfr` files written by `DELETE /actuator/profiling` |

This property is not in `application.yml`; the default lives only in the `@Value` expression. See [Observability](../systems/observability.md).

## Env vars read directly by code

| Env var | Default | Read in | Effect |
| --- | --- | --- | --- |
| `MOCKINGBIRD_DEV_PASSWORD` | `mockingbird` | `src/main/java/com/mockingbirdbank/config/DataInitializer.java` (`System.getenv()`) | Password for the seeded `jordan.ellis` login. Only used when the database is empty at startup |
| `TESTCONTAINERS_HOST_OVERRIDE` | unset | Testcontainers library | Set to `host.docker.internal` in `.devcontainer/docker-compose.yml` so tests inside the dev container reach sibling containers |
| `HARNESS_JAVA_AGENT` (JVM system property, not env var) | unset | `build.gradle` (`tasks.withType(Test)`) | When set, appended to the test JVM args for Harness CI Test Intelligence |

## Build properties

Gradle properties can be passed as `-P<name>=<value>` or set in `gradle.properties`.

| Property | Default | Source | Effect |
| --- | --- | --- | --- |
| `baseVersion` | `0.1.0` | `gradle.properties` | Prefix of the computed version `<baseVersion>-<short sha>[-dirty]`; also read by `scripts/build-metadata.sh` |
| `vaadin.productionMode` | `false` | `gradle.properties` and the `vaadin {}` block in `build.gradle` | `true` builds the optimized frontend bundle; the Dockerfile and DAST workflow pass `-Pvaadin.productionMode=true` |
| `org.gradle.jvmargs` | `-Xmx1g` | `gradle.properties` | Gradle daemon heap |
| `appVersion` | unset | `build.gradle` line 54 | Replaces the whole computed version. Release builds pass the tag without the `v` |
| `gitCommit` | `git rev-parse HEAD`, else `unknown` | `build.gradle` line 41 | Full commit for `git.properties` |
| `gitBranch` | `git rev-parse --abbrev-ref HEAD` (`detached` for a detached HEAD), else `unknown` | `build.gradle` lines 43-47 | Branch for `git.properties` |
| `gitDirty` | from `git status --porcelain`, else `unknown` | `build.gradle` lines 49-51 | Adds `-dirty` to the version when `true` |
| `gitCommitTime` | `git show -s --format=%ct HEAD` | `build.gradle` line 52 | Commit time (epoch seconds) in `git.properties`, omitted if unknown |
| `buildTime` | unset | `build.gradle` `springBoot.buildInfo` | Written as `build.time` in `build-info.properties`; when unset the field is excluded so local builds stay cacheable |

When `.git` is missing and no `-P` values are passed, `gitCommitShort` becomes `unknown`, so the version reads `0.1.0-unknown`. See [Build version](../features/build-version.md).

The Gradle wrapper pins Gradle 9.5.1 in `gradle/wrapper/gradle-wrapper.properties`. The Java toolchain is 21 (`build.gradle`, `java.toolchain`).

## Docker

### Build args

Declared in `Dockerfile` and produced by `scripts/build-metadata.sh` (`--docker-args` prints them as `--build-arg` flags).

| Build arg | Default | Passed to Gradle as | Also used for |
| --- | --- | --- | --- |
| `APP_VERSION` | empty | `-PappVersion` | Label `org.opencontainers.image.version` |
| `GIT_COMMIT` | empty | `-PgitCommit` | Label `org.opencontainers.image.revision` |
| `GIT_BRANCH` | empty | `-PgitBranch` | |
| `GIT_DIRTY` | empty | `-PgitDirty` | |
| `GIT_COMMIT_TIME` | empty | `-PgitCommitTime` | |
| (none) | `date -u` at build time | `-PbuildTime` | Always set inside the build stage |

`scripts/build-metadata.sh` honors an `APP_VERSION` env var as an override; `.github/workflows/release.yml` sets it from the tag. The runtime image exposes port 8080 and runs `java -jar app.jar`. See [Docker image](../deployment/docker-image.md).

### `.env.example`

Template for `.env`, which `runDockerImage.zsh` sources and passes with `--env-file`. `.env` is gitignored and excluded by `.dockerignore`.

| Variable | Example value | Note |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://host.docker.internal:5432/mydatabase` | Reaches a Postgres on the host from inside the container |
| `SPRING_DATASOURCE_USERNAME` | `admin` | |
| `SPRING_DATASOURCE_PASSWORD` | `secret` | |
| `SERVER_PORT` | commented out (`8080`) | Change the `-p` mapping to match |

`runDockerImage.zsh` maps host port 8090 to container port 8080 and runs the image `ajpahl1008/mockingbird-bank:<version>`.

### `docker-compose.yml`

| Setting | Value |
| --- | --- |
| Service | `postgres`, image `postgres:16-alpine` |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `admin` / `secret` / `mydatabase` (match the `application.yml` defaults) |
| Port | `5432:5432` |
| Volume | `mockingbird-postgres-data` at `/var/lib/postgresql/data` |
| Healthcheck | `pg_isready -U admin -d mydatabase`, every 5s, 10 retries |

### Dev container

`.devcontainer/docker-compose.yml` merges with the root `docker-compose.yml` and adds an `app` service.

| Setting | Value |
| --- | --- |
| Base image | `mcr.microsoft.com/devcontainers/java:1-21-bookworm` (`.devcontainer/Dockerfile`) |
| Features | Node 20, docker-outside-of-docker (`.devcontainer/devcontainer.json`) |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/mydatabase` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `admin` / `secret` |
| `TESTCONTAINERS_HOST_OVERRIDE` | `host.docker.internal` |
| Published ports | 8080 (app) and 5432 (from the root compose file) |
| `postCreateCommand` | Marks `/workspace` as a git safe directory, then runs `./gradlew --version` |

## Kubernetes

### Local k3d (`k8s/local/`)

From `k8s/local/kustomization.yaml` and the manifests it includes. See [Local k3d](../deployment/local-k3d.md).

| Setting | Value | Where |
| --- | --- | --- |
| Namespace | `mockingbird-bank-dev` | `kustomization.yaml`, `namespace.yaml` |
| Common labels | `app.kubernetes.io/part-of: mockingbird-bank`, `app.kubernetes.io/environment: local-dev` | `kustomization.yaml` |
| Secret `mockingbird-dev-db` | `POSTGRES_USER=admin`, `POSTGRES_PASSWORD` (throwaway local value), `POSTGRES_DB=mockingbirdbank` | `secretGenerator`; the app reads user and password from it |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://postgres:5432/mockingbirdbank` | `configMapGenerator` `mockingbird-dev-app-config` |
| `VAADIN_LAUNCH_BROWSER` | `false` | same ConfigMap |
| `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED` | `true` | same ConfigMap |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75` | same ConfigMap |
| `MANAGEMENT_TRACING_SAMPLING_PROBABILITY` | `0.0` | same ConfigMap |
| `MOCKINGBIRD_FEATURE_FLAGS_*` | none set (commented example) | same ConfigMap |
| App replicas / strategy | 1, RollingUpdate `maxSurge: 1`, `maxUnavailable: 0` | `app.yaml` |
| App probes | startup and liveness on `/actuator/health/liveness` (startup allows 36 x 5s), readiness on `/actuator/health` | `app.yaml` |
| App resources | requests 250m CPU / 512Mi, limit 1Gi memory | `app.yaml` |
| Postgres | StatefulSet `postgres`, `postgres:16-alpine`, `PGDATA=/var/lib/postgresql/data/pgdata`, 2Gi `local-path` PVC | `postgres.yaml` |
| Postgres resources | requests 100m CPU / 256Mi, limit 512Mi memory | `postgres.yaml` |
| Ingress | host `mockingbird.localhost`, class `traefik` | `ingress.yaml` |

`scripts/k3d-deploy.sh` reads three env vars:

| Env var | Default | Effect |
| --- | --- | --- |
| `CONTEXT` | `k3d-dev-k8s` | kube context every `kubectl` call is pinned to |
| `CLUSTER` | `dev-k8s` | k3d cluster for `k3d image import` and the loadbalancer port lookup |
| `ROLLOUT_TIMEOUT` | `300s` | Wait limit for the Postgres and app rollouts |

### LAB cluster (`k8s/*.yaml`)

Harness-templated manifests. Harness resolves `<+...>` expressions only in `k8s/values.yaml`; the manifests read them through `{{ .Values.x }}`. See [LAB cluster](../deployment/lab-cluster.md).

| Setting | Value | Where |
| --- | --- | --- |
| `image` | `<+artifact.image>` | `k8s/values.yaml` |
| `dbUsername` | `admin` | `k8s/values.yaml` |
| `dbPassword` | `<+secrets.getValue("postgres-lab-secret")>` (Harness secret manager) | `k8s/values.yaml` |
| Namespace | `mockingbird-bank` | `k8s/00-namespace.yaml` |
| Secret `mockingbird-db-credentials` | `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` from the values above | `k8s/01-app-db-secret.yaml` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://192.168.0.148:5432/mockingbirdbank` (external Postgres) | ConfigMap in `k8s/02-app-configmap.yaml` |
| Replicas | 1 | `k8s/03-app.yaml` |
| Readiness probe | `/actuator/health`, 20s initial delay, every 10s | `k8s/03-app.yaml` |
| Service | `LoadBalancer`, port 80 to 8080, MetalLB IP `192.168.0.183` | `k8s/03-app.yaml` |

## CI secrets

Repository secrets referenced by `.github/workflows/`. `GITHUB_TOKEN` is provided by GitHub and needs no setup.

| Secret | Used by | Effect when missing |
| --- | --- | --- |
| `FACTORY_API_KEY` | `.github/workflows/droid-review.yml`, `.github/workflows/droid.yml`, `.github/workflows/droid-wiki-refresh.yml` | Droid review, `@droid` mentions and the wiki refresh cannot run |
| `FACTORY_WIKI_PR_TOKEN` | `wiki-publish` job in `.github/workflows/droid-wiki-refresh.yml` | Optional fine-grained PAT (contents and pull-requests write, this repository only). Without it the job falls back to `GITHUB_TOKEN`, and required checks do not run on the wiki PR |
| `SLACK_ALERTS_WEBHOOK_URL` | `notify-on-failure` job in `.github/workflows/ci.yml`; last step of `deployment-smoke-test` in `.github/workflows/publish-image.yml` | The step logs a skip message and exits 0 |
| `GITHUB_TOKEN` | GHCR login and pushes, GitHub Deployments API, release-drafter, gitleaks, the wiki PR when `FACTORY_WIKI_PR_TOKEN` is unset | Always present |

See [Alerting](../how-to-monitor/alerting.md) for the Slack messages.
