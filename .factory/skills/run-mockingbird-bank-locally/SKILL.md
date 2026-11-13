---
name: run-mockingbird-bank-locally
description: Use when you need to build, run, or interactively exercise the Mockingbird Bank Spring Boot + Vaadin app on a local machine, including standing up a local PostgreSQL instance, running the code-quality suite, and logging in with the seeded demo account.
---

# Run Mockingbird Bank locally

Mockingbird Bank is a single Spring Boot process (Vaadin UI + JPA/PostgreSQL persistence).
This skill covers getting it running end-to-end from a fresh clone, including a database when
you don't already have one reachable.

## 1. Prerequisites

- JDK 21 (Gradle's toolchain support auto-detects a local install)
- Docker, for either a throwaway local Postgres (below) or for running the test suite's
  Testcontainers-backed integration tests

Alternatively, skip local prerequisites entirely with the dev container
(`.devcontainer/`), which has JDK 21, Node, Docker CLI, and Postgres already
wired together - see step 2a below.

## 2. Stand up PostgreSQL

```bash
docker compose up -d
```

This starts a `postgres:16-alpine` matching the defaults in
`src/main/resources/application.yml` (`localhost:5432`, database
`mydatabase`, user `admin` / password `secret`), with a named volume so data
survives a restart. `docker compose down` stops it (`-v` to also drop the
volume). Skip this step if you already have a Postgres reachable at those
same defaults.

Flyway creates the schema automatically the first time the app starts against it - no manual
migration step needed.

### 2a. Or: use the dev container instead

```bash
npx @devcontainers/cli up --workspace-folder .
npx @devcontainers/cli exec --workspace-folder . -- ./gradlew build
```

(Or, in VS Code: "Dev Containers: Reopen in Container".) This gets you JDK 21,
Node, the Docker CLI (via `docker-outside-of-docker`, so Testcontainers-backed
tests work), and the same Postgres service from step 2 - already running and
already reachable - without installing anything on the host beyond Docker
itself. Ports 8080 and 5432 are published, so `bootRun` and a host-side
`curl localhost:8080/actuator/health` both work normally. Skip straight to
step 3 once it's up.

## 3. Build and run

```bash
./gradlew build     # compiles, runs tests, runs all code-quality checks (see step 5)
./gradlew bootRun    # starts the app on :8080 (override with SERVER_PORT)
```

If your Postgres isn't at the defaults above, pass the datasource as env vars instead:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db> \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<password> \
./gradlew bootRun
```

## 4. Log in and drive the UI

On an empty database, `DataInitializer` seeds two account holders with sample accounts and
transactions so there's real data to look at: `jordan.ellis` (three accounts, two of them in a
Regular program) and `riley.chen` (a customer under 18 with one savings account in a Youth
Savings program). Both logins use the same password. The program sections only show when the
app starts with `MOCKINGBIRD_FEATURE_FLAGS_SHOW_ACCOUNT_PROGRAMS=true`.

1. Open `http://localhost:8080` - you land on `LoginView`.
2. Sign in with username `jordan.ellis` (or `riley.chen`), password `mockingbird` (override the
   seeded password via the `MOCKINGBIRD_DEV_PASSWORD` env var before first startup if you need a
   different one).
3. You're redirected to the dashboard (`DashboardView`, routes `/` and `/accounts`): total
   balance banner plus one card per account.
4. Click any account card to open `AccountDetailView` (`/accounts/{id}`) and see that
   account's transaction history in a grid.
5. Use the "Back to accounts" link to return to the dashboard, or sign out via the main layout.

A 3xx redirect to `/login` for any authenticated route, or a `200` with `"status":"UP"` from
`GET /actuator/health`, both confirm the app is wired up correctly even without a browser.

To do the above automatically instead of by hand, run:

```bash
./scripts/run-interactive-qa.sh
```

It stands up its own throwaway Postgres (port 5433, so it won't collide with or get confused
by a database you already have on 5432), starts `bootRun`, waits for it to be healthy, then
uses Playwright to actually drive a Chromium browser through the login → dashboard → account
detail → sign-out flow above, plus a wrong-password rejection check. Tears everything down on
exit. First run needs `cd qa && npm install && npx playwright install chromium` once.

## 5. Run the code-quality suite

All of these run automatically as part of `./gradlew build`/`check`; run them individually
while iterating:

```bash
./gradlew spotlessCheck                    # formatting (fix with spotlessApply)
./gradlew checkstyleMain checkstyleTest    # naming/imports/complexity/file-size/TODOs
./gradlew pmdMain pmdTest                  # dead/unused code
./gradlew cpdCheck                         # copy-paste duplicate detection
```

See [AGENTS.md](../../../AGENTS.md) for the full command reference, project layout, and
conventions.
