# Pitfalls

These are the places where a reasonable change in Mockingbird Bank breaks something, or where the code behaves differently from what a newcomer expects. Each entry says what goes wrong, why, and what to do instead. The reasoning behind the underlying designs is in [design decisions](design-decisions.md).

## Security and data

### /accounts/{id} has no ownership check

`AccountDetailView.setParameter` calls `AccountService.requireAccount(accountId)`, which is a plain `findById` behind the circuit breaker. Neither it nor `TransactionService.forAccount` checks that the account belongs to the signed-in holder. Any authenticated user who edits the URL can see another holder's balance and transactions. Only `revealAccountNumber` filters by owner.

It is not exploitable today because the seed creates one holder, but it becomes a real IDOR the moment a second login exists. If you add users, fix this first: reuse the `isOwnedBy` check from `revealAccountNumber` and fail with the same not-found response for missing and foreign IDs, so callers cannot probe which IDs exist. See [account detail](../features/account-detail.md#no-ownership-check) and [cleanup opportunities](../cleanup-opportunities.md#bugs-and-gaps).

The Droid QA flow W11 seeds a second, login-less holder with `.factory/skills/qa-web/sql/seed-other-holder.sql` and opens its account as Jordan, so any QA run that selects W11 reports this as a high-severity FAIL until it is fixed. That FAIL is expected, not a regression from the PR under test. See [functional QA](../how-to-contribute/functional-qa.md#flow-menu).

### The seed runs on any empty database

`DataInitializer.run` returns early only if `account_holder` already has rows. Point the app at any fresh database, including a shared or LAB one, and it creates the `jordan.ellis` login with the password from `MOCKINGBIRD_DEV_PASSWORD` or the documented default `mockingbird`. There is no profile or flag guarding it. The class Javadoc still says "swap for a proper migration tool before this becomes a shared environment", although Flyway already exists. In any environment someone else can reach, set `MOCKINGBIRD_DEV_PASSWORD` or pre-populate the holder table before first start.

### Failed sign-ins log the typed username

`AuthenticationEventListener.onFailure` records the attempted username as an attribute on the `user.login` error event, and that line lands in `build/logs/analytics-events.log`, which keeps 30 days. A user who types their password into the username field leaves it on disk. `ScrubbingMessageConverter` only catches `password=`-style pairs, tokens and card-like digit runs, so it will not catch this.

## Database and migrations

### baseline-on-migrate skips V1 on a non-empty database

`spring.flyway.baseline-on-migrate: true` exists so pre-Flyway dev databases (built by `ddl-auto: update`) adopt Flyway without V1 failing. Flyway applies it to any non-empty schema with no history table: it records a baseline at version 1 and never runs V1.

That is what happened on the first LAB deploy. The target database (`metamanagement`) already held unrelated tables, so Flyway baselined and the app's own tables (`account`, `account_holder`, ...) were never created. Commit `b809e47` moved the app to a dedicated `mockingbirdbank` database and added `scripts/bootstrap-lab-db.sql` to create it by hand. Always give the app its own empty database. If a schema already has tables, expect V1 to be skipped.

### Never edit an applied migration

`src/main/resources/db/migration/V1__init_schema.sql` has been applied to local, k3d and LAB databases. Flyway stores a checksum for each applied script and refuses to start if the file changes. Add a new `V2__...sql` instead. Remember that `scripts/bootstrap-lab-db.sql` mirrors V1 by hand, so a schema change for LAB may need a matching manual step there too. See [persistence](../systems/persistence.md).

## Vaadin and Spring Security

### Layouts need their own @PermitAll

Vaadin's navigation access control checks the parent layout as well as the view. An unannotated layout defaults to `@DenyAll`, so every view routed through it is denied even if the view is `@PermitAll`. `MainLayout` carries `@PermitAll` for this reason (its Javadoc and commit `60f9881` explain it). Any new layout needs the same.

### MockMvc returns 403 after login (known non-issue)

`ApplicationSmokeTest.seededUserCanLogIn` stops at "login succeeded and the session holds the right principal". A follow-up `GET /accounts` under MockMvc returns 403 because Vaadin's servlet initializes lazily on its first real dispatch, and `RequestUtil.isSecuredFlowRoute()` treats an uninitialized service as "not secured", which falls through to the default-deny rule. It works against a live `bootRun`. Do not "fix" the test by loosening security; the background is in `AGENTS.md` ("Known non-issue") and commit `60f9881`.

### AuthenticationContext must be transient

Vaadin may serialize the UI tree, and `AuthenticationContext` is not `Serializable` by design. `MainLayout` holds it in a `private final transient` field. Any other component that keeps a reference must do the same.

### The eye button sits inside a link

`AccountCard` is an `Anchor` to `accounts/{id}`, and the dashboard puts an `AccountNumberDisplay` inside it. Its click listener calls `.preventDefault().stopPropagation()`; without them, clicking the eye would also navigate to the account page. Keep that if you change the listener or put the component inside any other clickable container. See [account number reveal](../features/account-number-reveal.md).

### Scaling past one replica loses sessions

Vaadin keeps each session's component tree in that pod's memory. A second replica behind a plain Service would send requests to a pod that has never seen the session. `k8s/03-app.yaml` and `k8s/local/app.yaml` both pin `replicas: 1`. Scaling out needs sticky sessions at the ingress or load balancer first.

## Resilience

### Business outcomes do not count as breaker failures

`protectedCall` in `AccountService` and `TransactionService` wraps only the repository call. The `orElseThrow` for a missing account runs outside it, so a burst of 404s never opens the breaker; only exceptions from the database do. Keep new `orElseThrow`, ownership checks and validation outside `protectedCall`.

A related gap: once a breaker opens, `ServiceUnavailableException` is caught only on the reveal paths (`AccountNumberDisplay.onToggle`, `DashboardView.toggleAll`). The `DashboardView` constructor, `MainLayout`'s `currentHolder()` call and `AccountDetailView.setParameter` let it reach `GlobalErrorHandler`, so the user gets Vaadin's generic error page. `AppUserDetailsService` is not behind a breaker at all. See [resilience](../systems/resilience.md).

## Configuration and flags

### Env var names need an underscore between words

Spring's relaxed binding recovers word boundaries from underscores. `MOCKINGBIRD_FEATURE_FLAGS_SHOW_WELCOME_BANNER=true` binds; `MOCKINGBIRD_FEATURE_FLAGS_SHOWWELCOMEBANNER=true` is silently ignored. This was found by running the app for real (commit `a8e08e8`) and is noted in the `FeatureFlagsProperties` Javadoc. Check `/actuator/featureflags` after changing one.

### Flags need a restart

`AGENTS.md` and the `application.yml` comment say a flag override needs "no code change or redeploy". It needs no code change, but `FeatureFlagsProperties` binds once at startup and nothing refreshes it (there is no `@RefreshScope`). Changing the env var requires a restart or a new pod. In k3d, edit the ConfigMap generator in `k8s/local/kustomization.yaml` and rerun `./scripts/k3d-deploy.sh`.

### Harness resolves expressions only in values.yaml

Harness substitutes `<+artifact.image>` and `<+secrets.getValue(...)>` only inside a Values YAML file, never inside raw manifests. Commit `b3f9fd0` moved them into `k8s/values.yaml`, and the manifests read them as Go templates (`{{ .Values.image }}`). Put any new Harness expression in `k8s/values.yaml` and reference it from the manifest. See [LAB cluster](../deployment/lab-cluster.md).

### A plain docker build reports 0.1.0-unknown

The Docker build context excludes `.git`, so version and commit arrive as build args from `scripts/build-metadata.sh`. Without them, `build.gradle` falls back to `<baseVersion>-unknown`, so the app reports version `0.1.0-unknown` with commit and branch `unknown`. The Dockerfile comment and `AGENTS.md` say it reports just "unknown". Use `./createLocalDockerImage.zsh`, `./scripts/k3d-deploy.sh`, or pass `$(scripts/build-metadata.sh --docker-args)`. See [build version](../features/build-version.md).

### /actuator/metrics is not exposed

`management.endpoints.web.exposure.include` is `health,info,featureflags,profiling`. The `AnalyticsEventService` Javadoc, `AGENTS.md`, the `application.yml` OTLP comment and `docs/runbooks/error-spike.md` all show `/actuator/metrics/product.events` queries. Those return 404 until `metrics` is added to the exposure list (and secured in `SecurityConfig`, since anything unmatched is denied). See [metrics](../how-to-monitor/metrics.md).

## Tooling and CI

### gitleaks flags test fixtures

`.github/workflows/secret-scan.yml` runs gitleaks on every push. Its generic-api-key heuristic has twice flagged fake values in tests: a token fixture in `ScrubbingMessageConverterTest` (commit `0d6ece3`, now marked `// gitleaks:allow`) and a long correlation ID literal next to `MDC_KEY` in `AnalyticsEventServiceTest` (commit `e1eaad1`, shortened). The second one also broke the next deployment record (commit `979d4da`). Use short, low-entropy placeholders in tests, or add an inline `gitleaks:allow` on a line that must look realistic.

### The AGENTS.md validator rejects paths that do not exist yet

`scripts/validate-agents-md.sh` (run by `validateAgentsMd` in `check`, the CI `fast-checks` job and the pre-commit hook) treats every backticked token in `AGENTS.md` that contains a `/` as a repo path and fails if it does not exist. Only paths starting with `build/` or `/` are skipped. Documenting a file in the same commit that adds it is fine; documenting a planned path, or a path inside an example command, fails the build. Write planned paths without backticks, and check that every `./gradlew <task>` you mention is a real task.

### Testcontainers in the dev container needs a host override

Inside the dev container, Testcontainers starts sibling containers on Docker's default bridge network, which the dev container (on a Compose network) cannot reach. `.devcontainer/docker-compose.yml` sets `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` to route through the host. If you build a different container setup, or run on a Docker engine without `host.docker.internal`, `ApplicationSmokeTest` and `AccountTransactionQueryPerformanceTest` will hang or fail to connect. Commit `1215ae5` lists this along with two other dev-container traps.

### A bad FACTORY_WIKI_PR_TOKEN looks like a missing credential

When the `FACTORY_WIKI_PR_TOKEN` secret is set, `peter-evans/create-pull-request` in `.github/workflows/droid-wiki-refresh.yml` replaces the checkout credentials with that token, and the `github.token` fallback is never used. If GitHub rejects the token (expired, revoked, mistyped, or pasted with whitespace), both the fetch of `factory/droid-wiki` and the push fail with "could not read Username for 'https://github.com'", which reads like no credential was configured at all. A valid token without enough permission fails differently, with a 403 on the push. The publish step has `continue-on-error: true`, so the run still shows success and only a `::warning::` annotation reports it.

The first refresh runs on `master` hit exactly this. `2b3e914` (PR #5) moved both checkout steps from v4 to v6 on the theory that the checkout version was the cause; the next run failed the same way, and replacing the secret with a valid fine-grained token fixed it. The run logs show the same failure under checkout v4 and v6. If the warning appears, check the token before changing the workflow. See [tooling](../how-to-contribute/tooling.md#wiki-refresh).

### A green QA check does not mean QA passed

Every setup and agent step in `.github/workflows/qa.yml` has `continue-on-error: true`, so the "Functional QA (Droid)" job normally succeeds even when the agent reports FAIL or BLOCKED rows, or crashes. The result is the "QA Report" comment, not the check status. Read the comment. If the job was skipped (draft, fork or Dependabot PR), there is no comment at all, which is easy to mistake for a pass. See [functional QA](../how-to-contribute/functional-qa.md#the-qa-workflow).

### Seeding the QA holder before first start breaks the demo login

`DataInitializer` seeds Jordan only when `account_holder` is empty. Running `.factory/skills/qa-web/sql/seed-other-holder.sql` against a fresh database, before the app has started once, leaves one holder with no login, and the demo sign-in then fails with valid credentials. Run the cleanup SQL and restart the app. On k3d and local, the database outlives the run, so a crashed QA run can leave "QA Other Checking" behind; both SQL files are safe to re-run.
