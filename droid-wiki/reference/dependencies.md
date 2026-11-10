# Dependencies

Every Gradle plugin and library declared in `build.gradle`, the npm package in `qa/`, the Docker images the project runs, and the GitHub Actions its workflows call. Most library versions come from a BOM rather than being written next to the dependency; the "Version" column says which.

## Counts

| What | Count | Source |
| --- | --- | --- |
| Gradle plugins | 11 (4 core, 7 third-party) | `build.gradle` `plugins {}` |
| Imported BOMs | 2 explicit (Vaadin, Testcontainers) plus Spring Boot's, applied by `io.spring.dependency-management` | `build.gradle` `dependencyManagement {}` |
| Dependency declarations | 29 (27 distinct artifacts; Lombok is declared 3 times) | `build.gradle` `dependencies {}` |
| Declarations with a version written inline | 2 (both Resilience4j) | `build.gradle` |
| Pinned analysis tool versions | 5 (Checkstyle, PMD, CPD, JaCoCo, google-java-format) | `build.gradle` |
| npm direct dependencies | 1 (`qa/package.json`), 3 packages in `qa/package-lock.json` | `qa/` |
| Distinct GitHub Actions | 17 repositories, 19 action paths | `.github/workflows/*.yml` |

## Gradle plugins

| Plugin | Version | Why |
| --- | --- | --- |
| `java` | Gradle core | Java compilation; toolchain pinned to 21 |
| `jacoco` | Gradle core (tool 0.8.12) | Coverage report and the 30% / 90% gates |
| `checkstyle` | Gradle core (tool 10.20.2) | Style and complexity rules from `config/checkstyle/checkstyle.xml` |
| `pmd` | Gradle core (tool 7.7.0) | Dead-code rules from `config/pmd/dead-code.xml` |
| `com.diffplug.spotless` | 7.0.2 | Formatting with google-java-format 1.25.2 (AOSP style), unused import removal |
| `de.aaschmid.cpd` | 3.5 (CPD tool 7.7.0) | Copy-paste detection, minimum 75 tokens |
| `org.gradle.test-retry` | 1.6.2 | Re-runs a failing test up to 2 times; feeds `flaky.txt` |
| `com.autonomousapps.dependency-analysis` | 3.19.2 | `buildHealth`: fails on unused or wrongly scoped dependencies (see below) |
| `org.springframework.boot` | 4.1.1 | `bootRun`, `bootJar`, `buildInfo` |
| `io.spring.dependency-management` | 1.1.6 | Applies the Spring Boot BOM and the extra BOM imports |
| `com.vaadin` | 25.3.0 | Builds the Vaadin frontend bundle; `vaadin.productionMode` switch |

The Gradle wrapper itself is 9.5.1 (`gradle/wrapper/gradle-wrapper.properties`). Repositories are Maven Central and `https://maven.vaadin.com/vaadin-addons`.

## BOMs

| BOM | Version | Why |
| --- | --- | --- |
| Spring Boot | 4.1.1 (from the plugin) | Versions for every Spring, Hibernate, Flyway, Micrometer, Logback, Lombok and PostgreSQL driver artifact below |
| `com.vaadin:vaadin-bom` | 25.3.0 | Versions for all `com.vaadin` artifacts |
| `org.testcontainers:testcontainers-bom` | 2.0.5 | Kept in sync by hand with the Testcontainers version Spring Boot 4.1.1 manages, because the dependency-management plugin does not resolve that nested import here |

## Libraries

### Runtime

| Artifact | Configuration | Version | Why |
| --- | --- | --- | --- |
| `com.vaadin:vaadin-spring-boot-starter` | implementation | Vaadin BOM | Vaadin Flow UI on Spring Boot |
| `org.springframework.boot:spring-boot-starter-data-jpa` | implementation | Spring Boot BOM | JPA entities and Spring Data repositories |
| `org.springframework.boot:spring-boot-starter-web` | implementation | Spring Boot BOM | Embedded Tomcat and Spring MVC |
| `org.springframework.boot:spring-boot-starter-actuator` | implementation | Spring Boot BOM | `/actuator/health`, `/actuator/info` and the custom endpoints |
| `org.springframework.boot:spring-boot-starter-security` | implementation | Spring Boot BOM | Form login, route protection |
| `org.springframework.boot:spring-boot-starter-flyway` | implementation | Spring Boot BOM | Spring Boot 4 moved Flyway auto-configuration into this starter; `flyway-core` alone no longer triggers it |
| `org.flywaydb:flyway-database-postgresql` | runtimeOnly | Spring Boot BOM | Flyway's PostgreSQL support, unbundled since Flyway 10. Runtime SPI, so `runtimeOnly` (confirmed by `buildHealth`) |
| `org.springframework:spring-context` | implementation | Spring Boot BOM | Referenced directly (`SecurityConfig` and others) instead of relying on a starter's transitive pull |
| `org.springframework:spring-tx` | implementation | Spring Boot BOM | Direct use of `@Transactional` in the services |
| `ch.qos.logback:logback-classic` | implementation | Spring Boot BOM | `ScrubbingMessageConverter` extends a Logback class |
| `org.slf4j:slf4j-api` | implementation | Spring Boot BOM | Logger usage across the codebase |
| `io.micrometer:micrometer-core` | implementation | Spring Boot BOM | `AnalyticsEventService` compiles against `Counter` and `MeterRegistry` |
| `io.github.resilience4j:resilience4j-circuitbreaker` | implementation | 2.4.0 | Circuit breakers in `ResilienceConfig`. Plain Java because `resilience4j-spring-boot3` targets Spring Boot 3 APIs |
| `io.github.resilience4j:resilience4j-micrometer` | implementation | 2.4.0 | `TaggedCircuitBreakerMetrics` binding to Micrometer |
| `org.springframework.boot:spring-boot-starter-opentelemetry` | implementation | Spring Boot BOM | HTTP request tracing and OTLP span export, active from the classpath alone |
| `org.postgresql:postgresql` | runtimeOnly | Spring Boot BOM | JDBC driver |
| `org.projectlombok:lombok` | compileOnly, annotationProcessor, testAnnotationProcessor | Spring Boot BOM | Entity accessors and constructors. The test processor entry exists because tests construct Lombok-annotated entities (flagged by `buildHealth`) |

### Development only

| Artifact | Version | Why |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-devtools` | Spring Boot BOM | Restart on change during `bootRun`; excluded from `bootJar` |
| `com.vaadin:vaadin-dev` | Vaadin BOM | Vaadin dev server and dev tools; excluded from production builds |

### Test

| Artifact | Version | Why |
| --- | --- | --- |
| `org.springframework.boot:spring-boot-starter-test` | Spring Boot BOM | JUnit 5, Mockito, AssertJ, Spring test support |
| `org.springframework.boot:spring-boot-webmvc-test` | Spring Boot BOM | MockMvc support in Spring Boot 4's split test modules |
| `org.springframework.boot:spring-boot-testcontainers` | Spring Boot BOM | `@ServiceConnection` wiring of the Postgres container |
| `org.testcontainers:testcontainers-junit-jupiter` | Testcontainers BOM | `@Testcontainers` / `@Container` lifecycle |
| `org.testcontainers:testcontainers-postgresql` | Testcontainers BOM | `PostgreSQLContainer` (`postgres:16-alpine`) |
| `org.springframework.security:spring-security-test` | Spring Boot BOM | Security test helpers for MockMvc |
| `org.flywaydb:flyway-core` | Spring Boot BOM | `ProgramMigrationTest` drives Flyway directly (migrate to V1, insert data, then migrate on) instead of through Spring Boot's auto-configuration |
| `com.vaadin:vaadin-testbench-junit5` | Vaadin BOM | Vaadin TestBench for JUnit 5 (pulls in Selenium). No test class under `src/test/java` imports it today; `buildHealth` does not flag it because the `vaadin` bundle counts any `com.vaadin` reference as use |

## dependency-analysis plugin

`./gradlew buildHealth` runs as part of `check` and in the `fast-checks` CI job. The `dependencyAnalysis {}` block in `build.gradle` sets `onUnusedDependencies` and `onIncorrectConfiguration` to `fail`, so an unused dependency or one declared at the wrong scope breaks the build.

Starters are never referenced by class name, so the block groups each framework into a bundle that counts as used when any member is referenced:

| Bundle | Members |
| --- | --- |
| `spring-boot` | group `org.springframework.boot` |
| `spring-framework` | group `org.springframework` |
| `spring-security` | group `org.springframework.security`, `spring-boot-starter-security` |
| `spring-data` | group `org.springframework.data`, `spring-boot-starter-data-jpa` |
| `hibernate` | groups `org.hibernate.orm`, `org.hibernate.validator`, the Jakarta Persistence and Validation APIs, `spring-boot-starter-data-jpa` |
| `web` | group `org.apache.tomcat.embed`, `spring-web`, `reactor-core`, `spring-boot-starter-web` |
| `logging` | groups `ch.qos.logback`, `org.apache.logging.log4j`, `slf4j-api` |
| `vaadin` | group `com.vaadin` |
| `junit` | groups `org.junit.jupiter`, `org.junit.platform`, `opentest4j`, `spring-boot-starter-test` |
| `test-support` | AssertJ, Hamcrest, Mockito, `spring-boot-test`, `spring-beans`, `spring-test`, `spring-boot-starter-test` |
| `opentelemetry` | group `io.opentelemetry`, `testcontainers-junit-jupiter` |
| `selenium` | group `org.seleniumhq.selenium`, `vaadin-testbench-junit5` |

Several direct declarations above (`spring-context`, `spring-tx`, `logback-classic`, `slf4j-api`, `micrometer-core`, the Lombok test processor, the `runtimeOnly` Flyway module) exist because `buildHealth` flagged them. See [Tooling](../how-to-contribute/tooling.md).

## npm (`qa/`)

| Package | Version | Why |
| --- | --- | --- |
| `@playwright/test` | 1.63.0 (exact) | Drives Chromium for `scripts/run-interactive-qa.sh`; pulls in `playwright` and `playwright-core` 1.63.0, which need Node 20 or later |

The root `package.json` and `package-lock.json` are generated by the Vaadin Gradle plugin and are gitignored, so they are not tracked in the repository.

## Docker images

| Image | Used in | Why |
| --- | --- | --- |
| `eclipse-temurin:21-jdk` | `Dockerfile` build stage | Runs `./gradlew bootJar` in production mode |
| `eclipse-temurin:21-jre` | `Dockerfile` run stage | Slim runtime for `app.jar` |
| `postgres:16-alpine` | `docker-compose.yml`, `k8s/local/postgres.yaml`, the `wait-for-postgres` init container in `k8s/local/app.yaml`, `ApplicationSmokeTest`, `AccountTransactionQueryPerformanceTest`, `ProgramMigrationTest`, the smoke-test service in `.github/workflows/publish-image.yml` | Local, test and k3d database |
| `postgres:17` | Service container in `.github/workflows/dast.yml` | Database for the ZAP scan target |
| `postgres:17.2` | `scripts/run-interactive-qa.sh` | Throwaway database on port 5433 |
| `mcr.microsoft.com/devcontainers/java:1-21-bookworm` | `.devcontainer/Dockerfile` | Dev container base |

Dev container features (`.devcontainer/devcontainer.json`): `ghcr.io/devcontainers/features/node:1` (Node 20) and `ghcr.io/devcontainers/features/docker-outside-of-docker:1`, locked by digest in `.devcontainer/devcontainer-lock.json` at feature versions 1.7.1 and 1.10.1.

Published images go to `ghcr.io/<owner>/<repo>` from CI and to `ajpahl1008/mockingbird-bank` on Docker Hub from `createDockerHubImage.zsh`. See [Docker image](../deployment/docker-image.md) and [Releases](../deployment/releases.md).

## GitHub Actions

Every action except one is referenced by a major-version tag (or a branch) rather than a commit SHA. The exception is `peter-evans/create-pull-request` in `.github/workflows/droid-wiki-refresh.yml`, pinned to `5f6978faf089d4d20b00c7766989d076bb2fc7f1` (v8.1.1).

| Action | Ref | Workflows |
| --- | --- | --- |
| `actions/checkout` | `v5`; `v6` in `droid-wiki-refresh` | `ci`, `codeql`, `dast`, `droid-review`, `droid`, `publish-image`, `release`, `secret-scan`, `droid-wiki-refresh` |
| `actions/setup-java` | `v5` (Temurin 21) | `ci`, `codeql`, `dast` |
| `gradle/actions/setup-gradle` | `v4` | `ci`, `dast` |
| `actions/upload-artifact` | `v4` | `ci`, `dast`, `droid-wiki-refresh` |
| `actions/download-artifact` | `v4` | `droid-wiki-refresh` |
| `github/codeql-action/init`, `/autobuild`, `/analyze` | `v3` | `codeql` |
| `zaproxy/action-baseline` | `v0.14.0` | `dast` |
| `docker/login-action` | `v3` | `publish-image`, `release` |
| `docker/setup-buildx-action` | `v3` | `publish-image`, `release` |
| `docker/metadata-action` | `v5` | `publish-image` |
| `docker/build-push-action` | `v6` | `publish-image`, `release` |
| `release-drafter/release-drafter` | `v6` | `release-drafter`, `release` |
| `gitleaks/gitleaks-action` | `v2` | `secret-scan` |
| `actions/labeler` | `v5` | `labeler` |
| `actions/stale` | `v9` | `stale` |
| `Factory-AI/droid-action` | `main` (branch, not a tag) | `droid-review`, `droid` |
| `peter-evans/create-pull-request` | `5f6978f...` (SHA for v8.1.1) | `droid-wiki-refresh` |

`.github/workflows/droid-wiki-refresh.yml` also installs the Droid CLI itself with `curl -fsSL https://app.factory.ai/cli | sh`, which always fetches the latest release and is outside Dependabot's reach.

## Dependabot

`.github/dependabot.yml` checks five ecosystems weekly. Cooldowns hold back a freshly published version so a compromised or broken release has time to surface first.

| Ecosystem | Directory | Cooldown (default / major / minor / patch days) | Labels | Grouping |
| --- | --- | --- | --- | --- |
| `gradle` | `/` | 5 / 14 / 7 / 3 | `dependencies`, `gradle` | `test-and-quality-tooling`: JUnit, Mockito, Spotless, CPD, test-retry |
| `npm` | `/` | 5 / 14 / 7 / 3 | `dependencies`, `frontend` | none |
| `npm` | `/qa` | 5 / 14 / 7 / 3 | `dependencies`, `qa` | none |
| `docker` | `/` | 5 / 14 / - / - | `dependencies`, `docker` | none |
| `github-actions` | `/` | 5 / - / - / - | `dependencies`, `ci` | none |

The root `npm` entry points at a directory whose `package.json` is gitignored, so Dependabot has nothing to read there. The `docker` entry covers the root `Dockerfile`; image tags in `docker-compose.yml`, `k8s/local/`, the workflows, the test classes and `.devcontainer/Dockerfile` are outside its scope.
