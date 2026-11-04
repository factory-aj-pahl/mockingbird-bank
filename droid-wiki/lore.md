# Lore

Mockingbird Bank went from an empty repository to a scaffolded app, a LAB Kubernetes deployment and a full CI, security and observability toolchain in under 41 hours (2026-09-26 21:21 to 2026-09-28 13:27, US Eastern). After that came a quiet month and a few tracked feature PRs in November. All dates below come from `git log` on `master`; numbers are in [by the numbers](by-the-numbers.md).

```mermaid
graph LR
    A["Sep 26: scaffold"] --> B["Sep 27: hardening day"]
    B --> C["Sep 28: tooling sprint"]
    C --> D["Oct 1: dark mode"]
    D --> E["Oct 22: auth tests"]
    E --> F["Nov 3: SED-649 reveal"]
    F --> G["Nov 4: SED-727 version and k3d"]
    G --> H["Nov 4: wiki refresh CI"]
    H --> I["Nov 4: checkout v6 fix"]
```

## Era 1: the scaffold (2026-09-26)

One commit, `57f6ca0` "Initial scaffold: Vaadin + Spring Boot read-only account dashboard", added 29 files and 1,004 lines on the evening of Saturday 2026-09-26. It already had most of what a user sees today:

- The four entities (`Account`, `AccountHolder`, `Transaction`, `AccountType`) and three repositories.
- `AccountService`, `TransactionService`, `DashboardView`, `AccountDetailView`, `AccountCard`, `MainLayout` and the inline-SVG `BirdMark`.
- `DataInitializer` with the seeded demo data, including the "Direct Deposit - Bespin Engineering" transactions.
- An in-cluster Postgres for Kubernetes (`k8s/01-postgres-secret.yaml`, `k8s/02-postgres.yaml`) and a 53-line `build.gradle`.

There was no login, no tests, and the schema came from Hibernate's `ddl-auto: update`.

## Era 2: the hardening day (2026-09-27)

Sunday 2026-09-27 has 24 of the repository's 40 commits, from 10:11 to 22:05. In rough order:

**Container scripts, morning.** `44ea180` (10:11) added `scripts/create-postgres-container.zsh`, and `098c960` deleted it six minutes later as "removed uneeded script". The root-level `createLocalDockerImage.zsh`, `createDockerHubImage.zsh` and `runDockerImage.zsh` arrived at 10:22 (`1f167d7`). `ef23e06` (10:58) fixed a port mismatch in `runDockerImage.zsh` (8090 vs the app's 8080), added `.dockerignore` and `.env.example`, and moved the datasource settings to explicit env-var overrides.

**Tests, login and schema, late morning.** `13d5c67` (11:17) added the first unit tests and the boot smoke test. `60f9881` (11:48) added Spring Security form login. `b64da02` (12:11) replaced `ddl-auto: update` with Flyway. Its message says `V1__init_schema.sql` mirrors what Hibernate had been generating, and that `baseline-on-migrate` exists for databases created before that day. Thirty seconds later `ce54a61` moved the checked-in k8s Postgres secret to a gitignored `.example` template. `66878eb` (12:32) committed the Gradle wrapper (9.5.1) and dropped the Dockerfile's `gradle:8.14-jdk21` base image.

**The LAB deployment, afternoon and evening.** `bee950d` (15:37) added Harness Test Intelligence hooks to `build.gradle`. `278a4eb` (16:16) pointed the manifests at an external LAB Postgres and deleted the in-cluster StatefulSet and its secret template. `b3f9fd0` (18:09) added `k8s/values.yaml` after finding that Harness only resolves `<+...>` expressions in a values file. `b809e47` (19:14) moved the app to a dedicated `mockingbirdbank` database: the first target already contained unrelated tables, so `baseline-on-migrate` marked V1 as applied and the app's own tables were never created. `scripts/bootstrap-lab-db.sql` dates from this commit. `47b5026` (19:24) switched the Service from ClusterIP to LoadBalancer, and `39aca95` (19:39) pinned the MetalLB IP. See [LAB cluster](deployment/lab-cluster.md).

**Quality and security, night.** In one hour, starting at 21:06:

| Time | Commit | Change |
|---|---|---|
| 21:06 | `0eb91ef` | Spotless, Checkstyle, PMD, CPD, the N+1 query guard, the first `AGENTS.md` |
| 21:10 | `3cc3a4a` | Per-test timing report |
| 21:25 | `9d42417` | Test retry and flaky-test report |
| 21:30 | `b144ea8` | JaCoCo coverage thresholds |
| 21:41 | `a3627de` | Playwright interactive QA suite in `qa/` |
| 21:57 | `00b53d6` | CI, CodeQL, gitleaks, ZAP DAST, Dependabot, CODEOWNERS, log scrubbing |
| 22:02 | `0d6ece3` | Allow-list a fake token that gitleaks flagged |
| 22:05 | `1282c95` | Turn off ZAP issue filing in the DAST workflow |

`00b53d6` is also when `DataInitializer` stopped logging the seeded demo password in plain text.

## Era 3: the tooling sprint (2026-09-28)

Monday morning opened with the repository's only Dependabot commit (`77317b7`, 07:57, Playwright 1.49.1 to 1.63.0). Then came a series of large batch commits, each named after a category of tooling:

- `1215ae5` (08:30): `docker-compose.yml` and the dev container. The commit message lists three bugs found while running it for real, including a Yarn apt source signed with a rotated key.
- `a8e08e8` (09:19): CI split into "Fast checks" and "Build and test", Dependency Analysis `buildHealth`, build-performance summaries, Droid review workflows, GHCR publishing, release-drafter, tag releases and the [feature flags](features/feature-flags.md).
- `1ab9d0f` (09:31): wired `jacocoTestReport` into `check`, after a CI annotation showed the HTML report had never been generated.
- `d263f9d` (09:56): issue and PR templates, labels, the stale-bot and `AnalyticsEventService`.
- `25c37cd` (13:18): circuit breakers, OpenTelemetry tracing, correlation IDs, the profiling endpoint, Slack alerting and the runbooks. At 1,181 added lines it is the largest commit in the history.
- `e1eaad1` (13:23) and `979d4da` (13:27): a second gitleaks false positive, and a 409 from the GitHub Deployments API that the first one had caused.

## Era 4: feature work (October and November 2026)

| Date | Commit | Change |
|---|---|---|
| 2026-10-01 | `1b43df4` | Dark mode toggle in `MainLayout`, semantic `--mb-*` CSS variables. The message quotes a request from the `#mbb-feedback` channel. |
| 2026-10-22 | `48ceede` | Unit tests for `AppUserDetailsService.loadUserByUsername` |
| 2026-11-03 | `09f771a` | SED-649: eye toggles to reveal full account numbers ([account number reveal](features/account-number-reveal.md)) |
| 2026-11-04 | `736990a` | SED-727: build version in `/actuator/info`, the startup log and the top bar ([build version](features/build-version.md)) |
| 2026-11-04 | `5fbf51f` | Local k3d deployment with its own in-cluster Postgres ([local k3d](deployment/local-k3d.md)) |
| 2026-11-04 | `3b03ecd` | `.github/workflows/droid-wiki-refresh.yml`, which regenerates this wiki on every push to `master` and proposes the pages as a PR ([tooling](how-to-contribute/tooling.md#wiki-refresh)) |
| 2026-11-04 | `2b3e914` | Both checkout steps in the wiki refresh moved from `actions/checkout@v4` to `@v6`, after the first run generated the wiki but could not push the rolling branch ([pitfalls](background/pitfalls.md#create-pull-request-v8-needs-checkout-v6)) |

The November commits are the first to arrive as numbered PRs (#1 to #5) and the first to carry Linear `SED-` keys. Dependabot's commit from September is also labelled "(#1)". GitHub numbers issues and PRs in one sequence per repository, so the two "#1"s probably come from different repositories, for example an earlier copy of this one. That is a guess; the git history does not say.

## Longest-standing pieces

These date from the scaffold on 2026-09-26 and are still in place:

- The data model and its three original repositories (`src/main/java/com/mockingbirdbank/model/`).
- `src/main/java/com/mockingbirdbank/ui/layout/BirdMark.java`, changed only once since (a Spotless reformat in `0eb91ef`).
- The dashboard and account detail views, although `DashboardView.java` (7 commits) and `AccountDetailView.java` (6) are among the most-changed files.
- The seeded demo holder and the Bespin Engineering direct deposits in `DataInitializer`.

## Replaced and removed

| What | Replaced by | When | Commit |
|---|---|---|---|
| `scripts/create-postgres-container.zsh` | Nothing at first; `docker-compose.yml` arrived the next day | 2026-09-27, six minutes after it was added | `098c960` |
| Hibernate `ddl-auto: update` | Flyway migrations, Hibernate set to `validate` | 2026-09-27 | `b64da02` |
| Checked-in `k8s/01-postgres-secret.yaml` | A gitignored `.example` template | 2026-09-27 | `ce54a61` |
| Gradle from the build image (`gradle:8.14-jdk21`) | The committed Gradle 9.5.1 wrapper | 2026-09-27 | `66878eb` |
| In-cluster Postgres StatefulSet for k8s | The external LAB Postgres | 2026-09-27 | `278a4eb` |
| The `metamanagement` LAB database | A dedicated `mockingbirdbank` database | 2026-09-27 | `b809e47` |
| ClusterIP Service for the LAB deploy | LoadBalancer with a pinned MetalLB IP | 2026-09-27 | `47b5026`, `39aca95` |
| Logging the demo password at seed time | A log line pointing at `AGENTS.md` | 2026-09-27 | `00b53d6` |
| One CI job ("Build, test, and static analysis") | Two parallel jobs | 2026-09-28 | `a8e08e8` |

Two details are easy to misread:

- The in-cluster Postgres came back on 2026-11-04 (`5fbf51f`), but only for the local k3d flow in `k8s/local/`. The LAB manifests in `k8s/` still use the external database, and the local Service is ClusterIP behind a Traefik Ingress.
- `VaadinWebSecurity` was never used in this repository. `SecurityConfig` was written with `VaadinSecurityConfigurer` from its first commit (`60f9881`). The only trace of the older class is a Javadoc comment explaining that Vaadin 25 removed it.
