# Mockingbird Bank

A read-only account dashboard SPA: sign in, see your accounts and balances, drill into a
transaction history per account. Spring Boot 4 backend, Vaadin 25 (Flow + React) frontend,
PostgreSQL for storage.

For architecture, deeper conventions, and agent-oriented setup/build/test instructions, see
[AGENTS.md](AGENTS.md). This README covers the basics for a human getting started.

## Quick start

Requires JDK 21 and a reachable PostgreSQL instance (defaults to
`localhost:5432/mydatabase`, user `admin` / password `secret` - see
`src/main/resources/application.yml`). `docker compose up -d` starts a
matching local Postgres if you don't already have one.

```bash
docker compose up -d   # local Postgres, if you need one
./gradlew bootRun
```

Or, in one command: `./scripts/dev-up.sh` (starts Postgres, waits for it,
then runs the app).

Prefer not to install JDK 21/Postgres locally at all? Open this repo in the
dev container instead (`.devcontainer/` - "Dev Containers: Reopen in
Container" in VS Code, or `npx @devcontainers/cli up --workspace-folder .`
from a terminal): it has everything above pre-installed and pre-wired
together. See [AGENTS.md](AGENTS.md#dev-container) for details.

Open http://localhost:8080 and sign in with the seeded demo login:

- **Username:** `jordan.ellis`
- **Password:** `mockingbird` (override via the `MOCKINGBIRD_DEV_PASSWORD` env var)

Flyway creates the schema automatically on first startup; `DataInitializer` seeds one
account holder with a few sample accounts and transactions so the dashboard isn't empty.

## Building and testing

```bash
./gradlew build   # compile, run tests, run all code-quality checks
./gradlew test    # tests only (needs Docker for Testcontainers-backed integration tests)
```

See [AGENTS.md](AGENTS.md#code-quality-tooling) for the full list of linting/formatting/static
analysis tools (Checkstyle, Spotless, PMD, CPD) and how to run each individually.

## Architecture

See [docs/architecture.md](docs/architecture.md) for a diagram and description of how the
Vaadin UI, Spring Boot backend, and PostgreSQL database fit together.

## Running with Docker

```bash
./createLocalDockerImage.zsh   # build the image
./runDockerImage.zsh           # run it, reading datasource config from .env (see .env.example)
```

The production Docker image is a multi-stage build (`Dockerfile`): it compiles a Vaadin
production-mode `bootJar` in the build stage, then copies just the jar into a slim JRE
runtime image.

## Deployment

Kubernetes manifests for deploying this app (namespace, secrets, configmap, and the
app Deployment/Service) live under [`k8s/`](k8s/).

## License

Unlicensed / private project.
