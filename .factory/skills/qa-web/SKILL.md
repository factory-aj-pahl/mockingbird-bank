---
name: qa-web
description: >
  QA tests for the Mockingbird Bank web app (Spring Boot 4 + Vaadin 25, one process).
  Drives the UI through droid-control (sign-in, dashboard, account detail, account-number
  reveal, build version, dark mode, sign-out, cross-account isolation, account programs,
  feature-flag variants) and checks the Actuator endpoints with curl.
---

# QA: Mockingbird Bank web app

A read-only account dashboard. One Spring Boot process serves the Vaadin UI (server-side Flow
components, client-side routing) and the Actuator endpoints. Data comes from PostgreSQL;
`DataInitializer` seeds two holders on an empty database: Jordan Ellis (three accounts with eight
transactions each, two of them in a Regular program) and Riley Chen (a customer under 18 with one
savings account in a Youth Savings program).

Read `.factory/skills/qa/config.yaml` first: it defines the targets, the personas, how to reach
each target's database, and the `video_evidence` / `droid_control.compose` toggles.

## Interactive testing with Droid Control

Invoke the `droid-control` skill before interacting with the app. Use its web route
(agent-browser) for every browser flow, plus its Capture and Verify stages. Load Compose only
when both `video_evidence` and `droid_control.compose` are true in config.yaml. This file says
WHAT to test and what success looks like; droid-control owns HOW to drive, capture and verify.

Actuator checks are plain HTTP: use `curl` (no browser) unless the flow says otherwise.

## Testing Target

This repo has no preview deployments. Always test the code under test, never a remote
environment running other code. If neither option below works, report every web flow as
BLOCKED: "No running build of this branch available".

**`ci` (`QA_TARGET=ci`)** - `.github/workflows/qa.yml` already built the branch as a production
jar and started it. Use `$QA_BASE_URL` (http://localhost:8080). Do not rebuild. The workflow
also exports `QA_APP_JAR`, `QA_APP_PID_FILE` and `QA_APP_LOG` for restarts (feature flags) and
`QA_DB_HOST`/`QA_DB_PORT`/`QA_DB_NAME`/`QA_DB_USER`/`QA_DB_PASSWORD` for `psql`.

**`k3d` (default for local runs)** - the k3d deploy runs the working tree:

1. Check the cluster: `kubectl config get-contexts k3d-dev-k8s` and `k3d cluster get dev-k8s`.
   If either fails, switch to `local` below.
2. Read what is running: `curl -s http://mockingbird.localhost:81/actuator/info`. The response
   has `build.version` (`<base>-<sha>[-dirty]`) and `git.commit.id`.
3. Redeploy with `./scripts/k3d-deploy.sh` (allow ~15 minutes; a full image build takes about
   3) when the endpoint is unreachable, when `git.commit.id` differs from
   `git rev-parse --short HEAD`, or when `git status --porcelain` shows uncommitted changes in
   any `apps.web.path_patterns` path, or when the running `build.version` ends in `-dirty` (it
   was built from uncommitted changes that may no longer be in the tree). The script prints the real URL; use it instead of the
   config default if the port differs.
4. Wait for `GET <url>/actuator/health` to return `"status":"UP"`.

**`local` (fallback)** - `./scripts/dev-up.sh` starts the docker-compose Postgres and
`./gradlew bootRun` on :8080 from the working tree.

1. If something already answers on :8080, compare `/actuator/info` `git.commit.id` with
   `git rev-parse --short HEAD`. If it doesn't match, its `build.version` ends in `-dirty`, or
   the tree has uncommitted app changes, report BLOCKED and ask the user to stop that process. Never kill a process you didn't start.
2. Otherwise start `./scripts/dev-up.sh` as a long-running background process (use your shell
   tool's background/fire-and-forget mode, not plain `&`) and poll `/actuator/health` for up
   to 5 minutes. Stop it when the run is over.

Never use `lab` for branch QA (see its restrictions in config.yaml).

## Authentication

- Sign in through the Vaadin `LoginOverlay` at `/login`: fields `input[name="username"]` and
  `input[name="password"]`, button "Log in". Success lands on `/` (the dashboard). Failure
  redirects to `/login?error` and shows the overlay's error message.
- `holder` persona: username `jordan.ellis`. The password is whatever the app saw in
  `MOCKINGBIRD_DEV_PASSWORD` when it first seeded its database:
  - `ci`: `$MOCKINGBIRD_DEV_PASSWORD` (the workflow generates one per run for the app and QA).
  - `k3d`: always the default under "Demo login" in AGENTS.md. The pod never receives the
    variable, so ignore it even if your shell has it set.
  - `local`: the AGENTS.md default, unless `dev-up.sh` was started with the variable set (a
    `dev-up.sh` you start inherits your environment) when the database was first seeded. Never print the password in the
  report, logs or evidence.
- `youth_holder` persona: username `riley.chen`, with the same password as `holder` (both logins
  are seeded with it).
- No other secrets are needed for the app. `FACTORY_API_KEY` is only for the `droid` CLI.
- Sessions live in server memory: any app restart, pod rollout or flag change signs you out.
  Sign in again afterwards.

## Seed data reference (DataInitializer)

`src/main/java/com/mockingbirdbank/config/DataInitializer.java` is the source of truth for
these values. If the diff changes it, derive the expected values from the new code instead of
this table, and suggest a table update in the report.

| Account | Type tag | Masked | Full number | Balance |
| --- | --- | --- | --- | --- |
| Everyday Checking | Checking | `•••• 4821` | `1000000004821` | $4,318.52 |
| High-Yield Savings | Savings | `•••• 7743` | `2000000007743` | $18,200.00 |
| Vacation Savings | Savings | `•••• 0092` | `2000000000092` | $1,800.00 |

Total assets: **$24,318.52**, "Across 3 accounts". Account holder: "Jordan Ellis". Primary
account (hero): `Primary account •••• 4821`. Each account has the same 8 transactions (newest
first), including "Trader Joe's" (-$86.42), "Direct Deposit - Bespin Engineering" ($3,200.00, twice),
"Green Valley Utilities", "Transfer to Savings", "Blue Bottle Coffee", "Rent - Meridian
Apartments" and "Amazon". Opened dates: 2021-03-12, 2022-01-05, 2023-06-20.

Programs (visible only with `showAccountPrograms` on, see W15): Everyday Checking and
High-Yield Savings form Jordan's one **Regular** program (requirements met). Vacation Savings is
in no program.

`youth_holder` (Riley Chen, born 15 years before the seed date in America/New_York):

| Account | Type tag | Masked | Full number | Balance |
| --- | --- | --- | --- | --- |
| Youth Savings | Savings | `•••• 5317` | `2000000005317` | $245.50 |

Total assets **$245.50**, primary account `•••• 5317`, opened 2024-09-03. Four transactions
(newest first): "Birthday gift" ($50.00), "Weekly allowance" ($20.00), "Corner Bookstore"
(-$12.99), "Weekly allowance" ($20.00). The account forms Riley's one **Youth Savings** program
(requirements met).

Account IDs are database identities - never hard-code them. Read them from the dashboard card
links (`accounts/<id>`).

On k3d and local the database persists. If balances differ from the table, someone changed the
data: note it, compare against what the database actually holds, and only FAIL on behavior the
diff could have caused.

## Path-to-flow map

| Changed path | Run flows |
| --- | --- |
| `ui/view/LoginView.java`, `security/**`, `analytics/AuthenticationEventListener.java` | W1, W2, W3, W9, W12 |
| `ui/view/DashboardView.java`, `ui/view/AccountCard.java` | W4, W6, W15, W5 (adjacent) |
| `ui/view/AccountDetailView.java`, `service/TransactionService.java` | W5, W10, W11, W15 |
| `ui/view/AccountNumberDisplay.java`, `AccountService.reveal*` | W6, W11 |
| `service/AccountService.java`, `model/**`, `repository/**`, `db/migration/**` | W4, W5, W10, W11 |
| `config/DataInitializer.java` | W2, W4, W5, W15 |
| `service/Program*.java`, `ui/view/ProgramCard.java`, `ui/view/ProgramLookup.java`, `model/Program*.java`, `config/BusinessClockConfig.java` | W15, W13 |
| `ui/layout/MainLayout.java`, `ui/layout/BirdMark.java` | W7, W8, W9, W4 (adjacent) |
| `src/main/frontend/**` (theme CSS) | W4, W5, W8 (screenshots matter here) |
| `observability/**`, `build.gradle` build-info/git config, `gradle.properties` | W7, W12 |
| `config/FeatureFlag*.java`, `mockingbird.feature-flags` in `application.yml` | W13, W12 |
| `resilience/**`, `analytics/GlobalErrorHandler.java` | W14, W4 |
| Other `application.yml` changes, dependency bumps in `build.gradle` | Smoke set |

Anything not covered: write an ad-hoc flow that exercises the changed behavior directly.

**Smoke set** (explicit smoke runs, e.g. the workflow's manual dispatch, and broad changes):
W1, W2, W3, W4, W5, W6, W9, W12.

## Flow menu

Pick only the flows the diff calls for. Each lists persona, steps and pass criteria.

### W1. Signed-out redirect (anonymous)

1. With a fresh browser session, open `/`, then `/accounts`, then `/accounts/1`.
2. PASS: each lands on `/login` showing the "Mockingbird Bank" overlay with "Sign in to view
   your accounts"; no account data is visible at any point.

### W2. Sign in (holder)

1. Start from a fresh browser session. Vaadin remembers the last protected URL it blocked (for
   example from W1) and returns there after login instead of `/`. Open `/login`, enter the holder credentials, click "Log in".
2. PASS: URL becomes `/`; the top bar shows "Welcome back, Jordan Ellis", the dashboard shows
   "Total assets" and "Your accounts".

### W3. Wrong password rejected (anonymous, negative)

1. Open `/login`, enter `jordan.ellis` with a clearly wrong password, click "Log in".
2. PASS: URL is `/login?error`, the overlay shows its error message, nothing from the dashboard
   ("Welcome back", balances) appears. Also try an unknown username - same result, with no hint
   about which part was wrong.

### W4. Dashboard content (holder)

1. Sign in (W2).
2. PASS: hero shows "Total assets" **$24,318.52**, "Across 3 accounts", "Account holder"
   "Jordan Ellis" and "Primary account •••• 4821". "Your accounts" shows three cards in the
   order Everyday Checking, High-Yield Savings, Vacation Savings, each with its type tag, masked
   number and balance from the seed table, plus "View transactions →". No full account number
   is anywhere in the page text.

### W5. Account detail (holder)

1. From the dashboard, click the Everyday Checking card (not its eye button).
2. PASS: URL is `/accounts/<id>`; header shows "Everyday Checking", "Account •••• 4821",
   "· Opened 2021-03-12", "Current balance" "$4,318.52"; the grid has columns Date,
   Description, Category, Amount and 8 rows including "Trader Joe's" and "Direct Deposit -
   Bespin Engineering"; credits ($3,200.00, no plus sign) render in the positive color.
3. Click "← Back to accounts". PASS: returns to the dashboard with the same totals.
4. Repeat steps 1-2 for one savings account when the diff touches account types or balances.

### W6. Account number reveal (holder)

1. On the dashboard, click the eye button (`.account-number-toggle`, aria-label "Show full
   account number") on the Everyday Checking card.
2. PASS: the card shows `1000000004821`, the URL is still `/` (the click must not navigate),
   and the button's label changes to "Hide full account number".
3. Click it again. PASS: back to `•••• 4821`; `1000000004821` is gone from the page.
4. Click the toolbar button "Show account numbers" (aria-label "Show all account numbers").
   PASS: all three full numbers show (the checking number appears on the card and in the hero
   line); the button now reads "Hide account numbers".
5. Click "Hide account numbers". PASS: every number is masked again.
6. Auto re-mask (run when `AccountNumberDisplay` changed): reveal one number, wait 35 seconds
   without interacting, then PASS if it is masked again.
7. On an account detail page, the header eye reveals and re-masks that account's number.

### W7. Build version label (holder)

1. `curl -s <base>/actuator/info` and note `build.version` and `git.commit.id`.
2. Sign in. PASS: `#build-version` sits directly under the "Mockingbird Bank" wordmark
   (left-aligned with it) and its text equals `build.version` (or `<version> (<commit>)` for a
   tagged release). Its tooltip (`title`) contains `commit <sha>, branch <branch>`.
3. Signed out, the label is not visible (the top bar only renders after sign-in).

### W8. Dark mode (holder)

1. Sign in, take a screenshot, click "Dark mode".
2. PASS: `document.documentElement` gets `theme="dark"`, the page visibly switches to the dark
   palette (screenshot), and `localStorage['mbb-theme']` is `dark`.
3. Reload the page. PASS: it is still dark (a brief light flash on load is a known, accepted
   quirk).
4. Click the button again. PASS: theme attribute and localStorage key are removed. Always leave
   the theme light at the end of the flow.

### W9. Sign out (holder)

1. Signed in, click "Sign out".
2. PASS: lands on `/login`. Then open `/` directly: you are redirected to `/login` again (the
   session is really gone, not just the page).

### W10. Unknown account (holder, negative)

1. Signed in, open `/accounts/999999999`, then `/accounts/not-a-number`.
2. PASS: Vaadin's not-found page ("Could not navigate to ...") with no stack trace, no SQL and
   no data from other accounts. In dev mode (`local` target) Vaadin also lists the available
   routes; that is expected dev tooling, not a finding. The top bar and session survive:
   navigating back to `/` still shows the dashboard.

### W11. Cross-account isolation (holder vs other_holder, negative)

1. Seed the second holder **after** the app is healthy (DataInitializer only seeds on an empty
   `account_holder` table, so seeding first would stop Jordan from being created):
   `<db_access command for the target> < .factory/skills/qa-web/sql/seed-other-holder.sql`.
   The output contains `OTHER_ACCOUNT_ID=<id>`.
2. Sign in as holder. PASS: the dashboard is unchanged (3 accounts, $24,318.52, no
   "QA Other Checking").
3. Open `/accounts/<OTHER_ACCOUNT_ID>` directly.
   PASS: the same not-found result as W10 (so account IDs can't be probed).
   FAIL (high severity, list it under Action Required): the page renders "QA Other Checking",
   "$777.77" or the transaction "QA-OTHER-HOLDER-MARKER". `AccountService.requireAccount` only
   returns accounts the signed-in holder owns, so a render here means that check regressed.
4. If the page did render, click its header eye button as a second row: PASS when the
   notification "We couldn't show the full account number right now. Please try again." appears
   and `9000000000017` never shows (`revealAccountNumber` checks ownership).
5. Always run cleanup, even after a failure:
   `<db_access command> < .factory/skills/qa-web/sql/cleanup-other-holder.sql`.

### W12. Actuator endpoint access (anonymous + holder)

1. `curl -si <base>/actuator/health`. PASS: 200 with `"status":"UP"`.
2. `curl -si <base>/actuator/info`. PASS: 200 with only `build` and `git` keys (no `java`,
   `os`, `env`).
3. `curl -si <base>/actuator/featureflags` and `curl -si <base>/actuator/profiling` with no
   session. PASS: anything except a 200 JSON body (Vaadin security usually redirects to
   `/login`; 401/403 are also fine).
4. `curl -si -H 'X-Correlation-Id: qa-<RUN_ID>' <base>/actuator/health`. PASS: the response
   echoes `X-Correlation-Id: qa-<RUN_ID>`; without the header, a generated ID comes back.
5. Signed in, open `/actuator/featureflags` in the browser. PASS: JSON with the six flags and
   the values expected for this target: the `mockingbird.feature-flags` defaults in
   `src/main/resources/application.yml` (as of the code under test), plus any override this run
   applied.
6. Do not `POST`/`DELETE` `/actuator/profiling` unless the diff touches `ProfilingEndpoint`.
   If it does, start and stop one short recording and confirm the stop response names a `.jfr`
   file. The endpoint needs a signed-in session and Spring Security's CSRF check still applies,
   so send the requests from the signed-in browser page (`fetch` with `credentials:
   'same-origin'`). If that returns 403, look for a CSRF token (`_csrf`/`_csrf_header` meta
   tags or an `XSRF-TOKEN` cookie) and resend it in the matching header. If no token source
   exists, report the step BLOCKED with what you tried rather than FAIL.

### W13. Feature flag variants (holder)

Run only the variants for flags the diff touches (or flagged UI it changes). Values are read at
startup, so each variant needs a restart with `MOCKINGBIRD_FEATURE_FLAGS_<NAME>=<value>`:

- **ci**: `kill "$(cat "$QA_APP_PID_FILE")"`, wait for :8080 to close, then start
  `MOCKINGBIRD_FEATURE_FLAGS_<NAME>=<value> java -jar "$QA_APP_JAR" >> "$QA_APP_LOG" 2>&1` as a
  long-running background process (your shell tool's background mode, not plain `&`), write its
  PID to `$QA_APP_PID_FILE`, and poll `/actuator/health`. Restore by restarting without the
  variable.
- **k3d**: `kubectl --context k3d-dev-k8s -n mockingbird-bank-dev set env
  deployment/mockingbird-app MOCKINGBIRD_FEATURE_FLAGS_<NAME>=<value>` then `kubectl --context
  k3d-dev-k8s -n mockingbird-bank-dev rollout status deployment/mockingbird-app`. Restore with
  `set env deployment/mockingbird-app MOCKINGBIRD_FEATURE_FLAGS_<NAME>-` and wait for the rollout.
- **local**: stop the `dev-up.sh` process you started and start it again with the variable set.

Confirm each override took effect via `/actuator/featureflags` before checking the UI.

| Flag (env suffix) | Variant | PASS when |
| --- | --- | --- |
| `SHOW_WELCOME_BANNER` | `true` | Banner "Hi Jordan - you're viewing an early preview of the redesigned dashboard." above the hero |
| `SHOW_ACCOUNT_NUMBER_ON_DASHBOARD` | `false` | Cards show no account number line (no blank gap); the hero "Primary account" line remains |
| `ALLOW_ACCOUNT_NUMBER_REVEAL` | `false` | Masked numbers are plain text: no eye buttons on cards, hero or detail header, and no "Show account numbers" toolbar |
| `SHOW_BUILD_VERSION` | `false` | No `#build-version` under the wordmark; `/actuator/info` still works |
| `SHOW_ACCOUNT_COUNT` | `true` | "You have 3 accounts" (`.account-count-summary`) directly under the "Your accounts" heading for the seeded holder; with the default (`false`) the line is absent |
| `SHOW_ACCOUNT_PROGRAMS` | `true` | "Your programs" below "Your accounts" and a "Program" section on account pages (run W15); with the default (`false`) neither appears and the dashboard shows no program notice |

Always restore the defaults and re-check `/actuator/featureflags` before finishing.

### W14. Database outage behavior (holder)

Only for `resilience/**` or `GlobalErrorHandler` changes, and only on `ci` or `local`. On k3d
the readiness probe takes the pod out of the ingress once the DB health check fails, so you would
only see Traefik's 503. If k3d is the only option, test through `kubectl --context k3d-dev-k8s -n
mockingbird-bank-dev port-forward deployment/mockingbird-app 8080:8080` against
`http://localhost:8080` instead of the ingress.

1. Sign in and load the dashboard.
2. Stop the database: ci -> `docker stop` the Postgres service container (find it with
   `docker ps --filter ancestor=postgres:16-alpine`); k3d -> `kubectl --context k3d-dev-k8s -n
   mockingbird-bank-dev scale statefulset/postgres --replicas=0`; local -> `docker compose stop
   postgres`.
3. Reload the dashboard repeatedly. Until the `accounts` circuit breaker opens, each request
   waits for Hikari's 30-second connection timeout before failing, and the breaker needs 10
   recorded calls, so expect roughly 5 minutes of reloads (cap the flow at about 7 minutes in CI).
   PASS: every request ends in an error page or error state instead of hanging past about 35
   seconds; once the breaker is open, requests fail within a couple of seconds; and
   `/actuator/health` reports `DOWN`. On `ci` (production build) the page must not show a stack
   trace. On `local` (dev mode), Vaadin's error page may include one, which is expected dev
   tooling, not a finding.
4. Restore the database (`docker start`, `--replicas=1` + `rollout status statefulset/postgres`,
   `docker compose start postgres`), wait ~10s for the breaker's half-open trial, reload. PASS:
   the dashboard recovers without restarting the app.

### W15. Account programs (holder + youth_holder, negative included)

Needs `SHOW_ACCOUNT_PROGRAMS=true` (restart steps in W13; confirm it via
`/actuator/featureflags`). Expected values are the seed's; on a persistent database check Known
Failure Mode 11 first.

1. Sign in as holder. PASS: a "Your programs" section sits below "Your accounts" with one card
   (`.program-card[data-program="REGULAR"]`) named "Regular", status "Requirements met", and
   links "Everyday Checking · Checking" and "High-Yield Savings · Savings" (Vacation Savings is
   not listed). There is no "Unmet requirements" box and no `.program-notice` on the page.
2. Click "High-Yield Savings · Savings". PASS: its account page has a "Program" section
   (`.account-program`) with the same Regular card. Back on the dashboard, open Vacation
   Savings. PASS: its "Program" section reads "This account isn't part of a program."
3. Unmet rule: first record Vacation Savings' current link with SQL (`<db_access command>`)
   so step 4 can put it back:
   `SELECT coalesce(program_participation_id::text, 'NULL') FROM account WHERE account_number =
   '2000000000092';` prints `NULL` on the seed. Any other value is a link that was there before
   the run; keep it for step 4. If it equals High-Yield Savings' participation (the subquery
   below), the account is already in the state this step creates, most likely left over from an
   interrupted run: see Known Failure Mode 5. Then link it into the same program:
   `UPDATE account SET program_participation_id = (SELECT program_participation_id FROM account
   WHERE account_number = '2000000007743') WHERE account_number = '2000000000092';`
   Reload the dashboard. PASS: the Regular card shows "Requirements not met", lists Vacation
   Savings too, and its "Unmet requirements" box reads "Regular needs exactly one savings
   account, but 2 are linked."; the `.program-notice` ("Some of your program requirements aren't
   met. ...") shows near the top of the page; total assets and the three account cards are
   unchanged.
4. Always restore the value recorded in step 3, even after a failure:
   `UPDATE account SET program_participation_id = <recorded value> WHERE account_number =
   '2000000000092';` (`<recorded value>` is `NULL` on the seed). Reload. PASS: back to the
   state in step 1.
5. Sign out and sign in as youth_holder. PASS: "Welcome back, Riley Chen", one Youth Savings
   account card ($245.50) and one "Youth Savings" program card with "Requirements met" that
   links "Youth Savings · Savings". Nothing of Jordan's (names, balances, programs) appears.
6. As youth_holder, open the URL of one of Jordan's accounts (from the links in step 1). PASS:
   the W10 not-found result.

## Error handling

- Health never reaches `UP`: collect the last 80 lines of the app log (`tail -n 80 "$QA_APP_LOG"` on ci,
  `kubectl --context k3d-dev-k8s -n mockingbird-bank-dev logs deployment/mockingbird-app --tail=80`
  on k3d (not `k3d-deploy.sh logs`, which follows forever), or the dev-up output on local) and report the affected flows
  as BLOCKED with that excerpt.
- `k3d-deploy.sh` fails or hangs on `kubectl`: report BLOCKED and point at AGENTS.md "Local
  Kubernetes (k3d)" (Docker VM sizing). Do not retry more than once.
- A Vaadin "Server connection lost" banner or a reconnect spinner means the app restarted or
  the session expired: sign in again and retry the step once before failing it.

## Known Failure Modes

1. **LoginOverlay is a web component.** The username/password inputs sit inside the Vaadin
   overlay; use the accessibility snapshot to find them rather than assuming plain DOM
   positions. The submit button's name is "Log in".
2. **Client-side routing.** Vaadin changes the URL without a full page load. Wait for expected
   text ("Your accounts", the account name) rather than load events.
3. **Slow first request in dev mode.** On the `local` target (`bootRun`), the first page load
   can take a minute or more while the frontend bundle compiles. Allow up to 90 seconds before
   failing it. The production jar (`ci`) and the k3d image are fast.
4. **Seeding order.** Seeding the `other_holder` before the app's first start on an empty
   database prevents DataInitializer from creating Jordan (it only runs when
   `account_holder` is empty). If sign-in fails with valid credentials and the holder is missing,
   run the cleanup SQL and restart the app, or ask the user before `k3d-deploy.sh down --purge-db`.
5. **Persistent databases.** k3d (PVC) and local (compose volume) keep data between runs. A
   previous run that crashed before cleanup leaves "QA Other Checking" behind; the seed SQL is
   idempotent and the cleanup SQL is safe to re-run. A run interrupted between W15 steps 3 and 4
   leaves Vacation Savings linked into Jordan's Regular program, and W15 step 1 then lists it
   with "Requirements not met". Step 4 puts back what step 3 recorded, so later runs keep that
   link instead of clearing it. Report it as leftover data, not a regression, and give the user
   the statement that returns the seed state:
   `UPDATE account SET program_participation_id = NULL WHERE account_number = '2000000000092';`
6. **Eye button inside a link.** Account cards are `<a>` elements. Click the eye button itself;
   clicking elsewhere on the card navigates to the detail page.
7. **Auto re-mask.** Revealed numbers re-mask after 30 seconds. Assert the revealed number
   promptly after clicking, and don't let long pauses between steps cause false failures.
8. **k3d port.** The loadbalancer maps Traefik to :81 on the usual setup, but the deploy script
   prints the real URL. Use it.
9. **Sessions are in memory.** Every restart, rollout or flag change signs you out.
10. **Bullet characters.** Masked numbers use `•` (U+2022), e.g. `•••• 4821`. Match on the last
    four digits if a text search for the bullets misbehaves.
11. **Seed from an older build.** DataInitializer never reseeds a database that already has
    holders. A k3d or local database first seeded before programs existed has no `riley.chen`
    login, no programs and no dates of birth. Report the affected W15 steps BLOCKED with that
    reason rather than FAIL, or ask the user before `k3d-deploy.sh down --purge-db`.
