# Getting started

This page gets you from a fresh clone to a running app and a green build. `AGENTS.md` has the full detail for each step.

## Prerequisites

| Need | Why | Check |
| --- | --- | --- |
| JDK 21 | Gradle toolchain in `build.gradle` targets Java 21 | `./gradlew -version` |
| Docker | Local Postgres via `docker-compose.yml`, and Testcontainers in the test suite | `docker info` |
| Node.js (optional) | Only for the Playwright QA suite in `qa/` | `node -v` |

Always use the checked-in wrapper `./gradlew`. Vaadin's Gradle plugin handles the frontend build; there is no separate `npm run dev` for the app.

If you prefer not to install anything, open the repo in the dev container (`.devcontainer/`). It ships JDK 21, Node, the Docker CLI and a Postgres service:

```bash
npx @devcontainers/cli up --workspace-folder .
npx @devcontainers/cli exec --workspace-folder . -- ./gradlew build
```

## Run the app

```bash
docker compose up -d     # postgres:16-alpine on localhost:5432, db mydatabase, admin/secret
./gradlew bootRun        # Flyway migrates, DataInitializer seeds, app listens on :8080
```

Or do both with one script: `./scripts/dev-up.sh`.

Open http://localhost:8080 and sign in with a demo login seeded by `src/main/java/com/mockingbirdbank/config/DataInitializer.java`:

- Username: `jordan.ellis` (an adult with three accounts and a Regular program) or `riley.chen` (a customer under 18 with one savings account and a Youth Savings program)
- Password: `mockingbird` for both (override with `MOCKINGBIRD_DEV_PASSWORD` before the first start)

Programs only appear with `MOCKINGBIRD_FEATURE_FLAGS_SHOW_ACCOUNT_PROGRAMS=true`. See [Account programs](../features/account-programs.md).

The seed only runs on an empty database. To start over, run `docker compose down -v`.

To point at a different database, set `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD`. Change the port with `SERVER_PORT`. All settings are listed in [Configuration](../reference/configuration.md).

## Build and test

```bash
./gradlew build                 # compile, tests, Spotless, Checkstyle, PMD, CPD, JaCoCo gates, AGENTS.md validation
./gradlew test                  # tests only; Docker must be running
./gradlew test --tests "com.mockingbirdbank.service.AccountServiceTest"
./gradlew spotlessApply         # fix formatting before committing
```

The first `./gradlew build` also installs the pre-commit hook in `.githooks/pre-commit` (Spotless and Checkstyle). Test reports land in `build/reports/tests/test/index.html`, with `timings.txt` and `flaky.txt` beside them. See [Testing](../how-to-contribute/testing.md) and [Tooling](../how-to-contribute/tooling.md).

## Browser smoke test

```bash
cd qa && npm install && npx playwright install chromium && cd ..   # once
./scripts/run-interactive-qa.sh
```

The script starts its own Postgres on port 5433, runs `bootRun`, waits for `/actuator/health`, then drives Chromium through sign-in, dashboard, account detail, sign-out and a wrong-password attempt.

In Droid, `/qa` does a diff-driven version of this: it picks the flows your change affects, tests the working tree on k3d (or `./scripts/dev-up.sh` as a fallback) and writes `qa-results/report.md`. See [Functional QA](../how-to-contribute/functional-qa.md).

## Check what is running

```bash
curl localhost:8080/actuator/health
curl localhost:8080/actuator/info      # version and commit, public
```

`/actuator/featureflags` and `/actuator/profiling` need a signed-in session. See [API](../api.md).

## Next steps

- Read the [Architecture](architecture.md) page.
- Follow the [Development workflow](../how-to-contribute/development-workflow.md) for branches, PRs and Linear issue keys.
- Deploy to the local cluster with `./scripts/k3d-deploy.sh` ([Local k3d](../deployment/local-k3d.md)).
