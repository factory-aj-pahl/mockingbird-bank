# Mockingbird Bank overview

Mockingbird Bank is a read-only account dashboard. A signed-in account holder sees their accounts and balances, then opens any of those accounts to read its transaction history. It is a single Spring Boot 4 process with a Vaadin 25 UI and one PostgreSQL database. There is no public registration: the app seeds two demo holders on first run.

## What it does

- **Sign in** with a username and password (Spring Security form login through a Vaadin `LoginOverlay`). See [Sign-in](../features/sign-in.md).
- **Dashboard** at `/` and `/accounts`: total assets across all accounts, then one card per account. See [Dashboard](../features/dashboard.md).
- **Account detail** at `/accounts/{id}`, for the signed-in holder's own accounts only: the account header, current balance, and a grid of transactions, newest first. See [Account detail](../features/account-detail.md).
- **Account programs** (behind the `showAccountPrograms` flag, off by default): the holder's Regular and Youth Savings programs, whether each meets its rules, and a message for every unmet rule. See [Account programs](../features/account-programs.md).
- **Reveal full account numbers** on request with an eye button, re-masked after 30 seconds. See [Account number reveal](../features/account-number-reveal.md).
- **Dark mode** toggle in the top bar, remembered in the browser's `localStorage`. See [UI](../systems/ui.md).

Nothing in the app writes banking data. The only writes are the first-run seed in `src/main/java/com/mockingbirdbank/config/DataInitializer.java` and Flyway's schema migration.

## Who works on it

The repository is a solo-maintained demo project (owner `ajpahl1008` in `.github/CODEOWNERS`). Much of the operational tooling (CI, release automation, alerting, runbooks, local Kubernetes) was added to make it behave like a production service, so the wiki covers that tooling in as much detail as the application code.

## Tech stack

| Layer | Technology | Where |
| --- | --- | --- |
| Language | Java 21 (Gradle toolchain) | `build.gradle` |
| Backend | Spring Boot 4.1.1, Spring Data JPA, Spring Security | `src/main/java/com/mockingbirdbank/` |
| UI | Vaadin 25.3.0 Flow (server-side Java views) | `src/main/java/com/mockingbirdbank/ui/` |
| Database | PostgreSQL 16, schema owned by Flyway | `src/main/resources/db/migration/` (`V1__init_schema.sql`, `V2__account_programs.sql`) |
| Resilience | Resilience4j circuit breakers | `src/main/java/com/mockingbirdbank/resilience/ResilienceConfig.java` |
| Observability | Micrometer, OpenTelemetry (OTLP), JDK Flight Recorder | `src/main/java/com/mockingbirdbank/observability/` |
| Build | Gradle wrapper with Spotless, Checkstyle, PMD, CPD, JaCoCo | `build.gradle`, `config/` |
| Browser QA | Playwright (separate npm project) | `qa/` |
| Functional QA | Droid `qa` skill with the `droid-control` plugin | `.factory/skills/qa/`, `.github/workflows/qa.yml` |
| Deployment | Docker, GitHub Actions, Harness-templated k8s, local k3d | `Dockerfile`, `.github/workflows/`, `k8s/` |

## Quick links

- [Architecture](architecture.md): how the pieces fit together
- [Getting started](getting-started.md): build, test, run locally
- [Glossary](glossary.md): project vocabulary
- [How to contribute](../how-to-contribute/index.md): workflow, testing, conventions
- [Features](../features/index.md) and [Systems](../systems/index.md): the code, by capability and by package
- [Deployment](../deployment/index.md) and [How to monitor](../how-to-monitor/index.md): running it
- [Reference](../reference/index.md): configuration, data model, dependencies

The repository's own docs are `AGENTS.md` (the most complete reference), `README.md` (titled "Mockingbird Bank - Retail" since PR #12 on 2026-11-07; earlier that day PR #10 renamed it "Mockingbird Bank Retail Website" and PR #11 changed it back to "Mockingbird Bank"), `docs/architecture.md`, and the runbooks in `docs/runbooks/`.
