---
name: qa
description: >
  Run QA tests for Mockingbird Bank. Analyzes git diff to determine affected areas,
  runs configured test flows with multiple personas, and generates diff-targeted tests.
  Uses droid-control for browser interactions and curl for Actuator endpoints.
  Use when testing PRs, releases, or smoke testing environments.
---

# QA Orchestrator

**SCOPE: This skill performs manual/functional QA only -- verifying that the application actually works by interacting with it as a real user would (browser, API calls). Do NOT run or report on CI checks, linting, Spotless, Checkstyle, PMD, unit tests, `./gradlew test`/`build`, the Playwright suite in `qa/`, or any static analysis. Those are handled by separate workflows.**

**Never silently skip a flow. If a flow cannot complete, report it as BLOCKED with what was tried and how the user can fix it.**

## Step 1: Load Configuration

Read `.factory/skills/qa/config.yaml` for environment URLs, credentials references, personas, and app definitions. It is the single source of truth; read it on every run.

For every affected interactive app, invoke the `droid-control` skill before interacting with the app. Follow its routing tables:

- Load the target driver selected for the app (`agent-browser` for `web`).
- Use the Capture and Verify stages on every run.
- Load Compose only when both `video_evidence: true` and `droid_control.compose: true`.
- When `droid_control.compose` is false (or `video_evidence` is false), never invoke Compose. Keep raw captures and text snapshots as evidence.

Treat `video_evidence` and `droid_control.compose` as runtime toggles: read them from config on every run and branch on their values; never assume a value.

## Step 2: Determine Target Environment

1. If `QA_TARGET` is set (the CI workflow sets `QA_TARGET=ci` and `QA_BASE_URL`), use it.
2. Otherwise use the environment the user named, else `default_target` from config.
3. Respect every `restrictions` entry for the chosen environment. In particular `lab` is never a valid target for PR/branch QA and must never be seeded or modified.
4. If the chosen target is `k3d` but the cluster is unavailable (`kubectl config get-contexts k3d-dev-k8s` fails, or `k3d cluster get dev-k8s` fails), fall back to `local` and say so in the report notes. Never fall back to `lab`.

There are no preview deployments for this repo. Every non-CI run tests the working tree via k3d or a local server; the `qa-web` skill explains how to make sure the running build matches the working tree.

## Step 3: Analyze Git Diff

Determine the diff base: `$QA_DIFF_BASE` if set, otherwise `origin/master` (run `git fetch origin master` first if the ref is missing). Collect changes with `git diff --name-only <base>...HEAD` plus uncommitted changes (`git diff --name-only HEAD` and untracked files from `git status --porcelain`).

Map changed files to apps using `apps.<name>.path_patterns` in config.yaml. Files that don't match ANY app's path_patterns (e.g., `.factory/**`, `docs/**`, `droid-wiki/**`, `.github/**`, `k8s/**`, `qa/**`, `src/test/**`, `AGENTS.md`, `README.md`) are NOT associated with any app. Do NOT run app test flows for them.

For each affected app:

- Run ONLY that app's relevant flows from its sub-skill
- Generate ADDITIONAL targeted tests based on the specific changes in the diff

For apps NOT affected by the diff: do NOT load or run their sub-skill, flows, or pre-flight checks.

If NO app is affected by the diff (docs-only, CI-only, test-only, or config-only changes), report as INCONCLUSIVE: "No app code changed -- QA not applicable for this diff." Do NOT run any app flows.

**Explicit override:** if the user (or the CI prompt) explicitly asks for a smoke run or names specific flows, run those flows regardless of the diff, and say so in the report.

## Step 4: Pre-flight Checks (affected apps only)

For `web` when affected:

- `droid-control@factory-plugins` is active (`droid plugin list --scope project`).
- The target is reachable and healthy: `curl -sf <base-url>/actuator/health` returns `"status":"UP"`.
- The running build is the code under test (see `qa-web` "Testing Target").
- Compose prerequisites from the droid-control skill -> only when `video_evidence` and `droid_control.compose` are both true. Let the droid-control Compose stage resolve its own plugin root; if the plugin's `remotion/node_modules` is missing, install from its lockfile in that `remotion` directory before rendering. Never install Remotion dependencies when Compose is off.

If a pre-flight check fails, report the affected flows as BLOCKED with the specific error and remediation steps. Never test a different build than the one under test.

## Step 5: Execute Diff-Relevant Flows Only

Read `.factory/skills/qa-web/SKILL.md`. It contains a MENU of flows plus a path-to-flow map. You must:

1. Read the diff carefully and identify which flows are relevant to the change.
2. Run those flows PLUS adjacent flows that verify the change integrates correctly (e.g., a change to `AccountService` also needs the dashboard totals and account detail checked).
3. Do NOT run unrelated flows.
4. If no existing flow covers the change, write a NEW ad-hoc test that directly verifies the changed behavior.
5. Do NOT run unit tests, lint, or any automated test suite.

## Step 6: Evidence Capture

Generate a run ID once (`RUN_ID=${RUN_ID:-$(date +%Y%m%d-%H%M%S)-$RANDOM}`) and save everything under `./qa-results/$RUN_ID/`.

Use **text snapshots as primary evidence** -- they render inline in the PR comment with no hosting issues.

For the web app (agent-browser):

- Use the `droid-control` web route; let its agent-browser atom own browser mechanics and its Capture atom own the recording lifecycle.
- Capture the page's accessibility tree as text evidence through the routed droid-control driver. Trim it to the relevant region.
- Save screenshot files to `./qa-results/$RUN_ID/` for the artifact upload.
- When `video_evidence` is false, or an upload fails: do NOT embed `![image](url)` markdown in the report; artifact URLs cannot be displayed inline in GitHub PR comments. Mention the filename and note it is in the downloadable artifacts.
- When `imagemagick: true` and a flow changes visible state (theme toggle, reveal, flag variant), build a before/after GIF from the two screenshots: `convert -delay 120 -loop 0 before.png after.png <flow-slug>-diff.gif` (or `magick` on ImageMagick 7). It goes to artifacts like any screenshot.

For Actuator/HTTP checks (curl): embed the status line and the relevant part of the body as a fenced code block. Never include cookies, session IDs, or passwords in evidence.

**Video evidence (only when `video_evidence: true` in config.yaml):**

1. Follow the droid-control Capture atom for recording start, interactions, recording stop, and raw-output verification. Record exactly one video per flow.
2. If `droid_control.compose` is false, keep the raw WebM. Upload it only when it is a verified playable video.
3. If `droid_control.compose` is true, hand the raw recording to the droid-control Compose atom and render `./qa-results/$RUN_ID/<flow-slug>.mp4`, using a `single` layout and the literal `"preset": "factory"`. No other preset is permitted. Trim dead time, add a concise title card, keep it at 60 seconds or less, and run the Compose atom's finalization checks.
4. Verify the evidence file exists and is non-empty. If capture or Compose fails, retry once; then fall back to text evidence and keep any raw capture in artifacts.
5. Upload via GitHub's user-attachments endpoint (undocumented; trust the live response):

   ```bash
   REPO_ID="${REPO_ID:-$(gh api "repos/$GITHUB_REPOSITORY" --jq .id)}"
   curl -sS --fail-with-body --request POST \
     --header "Authorization: Bearer $QA_EVIDENCE_TOKEN" \
     --header "Content-Type: application/octet-stream" \
     --header "Accept: application/json" \
     --header "X-GitHub-Api-Version: 2022-11-28" \
     "https://uploads.github.com/user-attachments/assets?name=<evidence-file>&content_type=<url-encoded-content-type>&repository_id=$REPO_ID" \
     --data-binary "@./qa-results/$RUN_ID/<evidence-file>" \
   | jq -e '.url'
   ```

   `QA_EVIDENCE_TOKEN` must be a classic PAT with `repo` scope. 404 = installation token (e.g. `GITHUB_TOKEN`); 403 `Resource not accessible by personal access token` = fine-grained PAT; 403 `Resource protected by organization SAML enforcement` = authorize the PAT for SSO. Use `video/webm` for raw WebM and `video/mp4` for Compose output.
6. Put the returned `url` alone on its own line under the flow's label in the Evidence section. NEVER wrap a video URL in `![name](url)`. Screenshots uploaded the same way DO use `![name](asset-url)`.
7. Never embed a link you have not verified came back from the upload API.

Evidence quality rules: focus on the relevant content, label each snapshot (what it shows and why it matters), and make each snapshot show something different.

## Step 7: Test Quality Gate

1. CHANGE-SPECIFIC FIRST. At least half the tests must directly verify the behavioral change in the diff.
2. INTEGRATION TESTS ARE VALID. Checking that the change integrates with adjacent features (dashboard totals still add up, sign-in still works) is fine.
3. NO UNRELATED FLOWS.
4. NO AUTOMATED TEST SUITES.
5. NEGATIVE TESTS. Include at least 1 test of error handling or a boundary condition related to the change (wrong password, unknown account, anonymous access, cross-account access).
6. INTERACTIVE TESTING. Interact with the app as a real user would.
7. INCONCLUSIVE IF UNSURE. If you cannot articulate what the change does, mark INCONCLUSIVE rather than PASS.

## Step 8: Handle Failures

Never silently skip a flow. If a flow cannot complete, report it as BLOCKED with what was tried and how the user can fix it, then continue with the next flow -- never abort the whole run for a single failure.

Always run cleanup before finishing, even after failures: the `other_holder` cleanup SQL if it was seeded, the W15 restore statement if Vacation Savings was linked into Jordan's program, any feature-flag override restored, the theme set back to light.

## Step 9: Generate Report

Write `./qa-results/report.md` following `.factory/skills/qa/REPORT-TEMPLATE.md`:

- Start with `## QA Report` followed by the results table.
- Result column uses: :white_check_mark: PASS, :x: FAIL, :no_entry: BLOCKED, :warning: FLAKY, :grey_question: INCONCLUSIVE.
- Keep it concise: table + short "Action Required" (if any) + one collapsed evidence block.
- Do NOT include a behavioral change summary, an info/metadata table, or verbose explanations of the diff.
- Do NOT report setup steps (build, deploy, startup, seeding) as test rows.
- Put ALL evidence in a single collapsed `<details>` block: labeled accessibility-tree/curl snapshots as fenced code blocks, plus screenshot/GIF filenames available in artifacts.
- With `video_evidence: true`, put each verified user-attachments video URL alone on its own line under its flow label; for any failed upload use the text-first rules.

## Step 10: Suggest Skill Updates (Failure Learning)

After the report, check whether any BLOCKED or FAIL result revealed a **testing environment insight** that would help future runs (not a selector typo, and not an expected behavior change from the diff). Examples: "k3d loadbalancer is on :8081 on this machine", "first page load after bootRun takes 90s while Vite compiles".

Read `failure_learning` from config.yaml. For `suggest_in_report`, append to the report (and only there; do NOT write `skill-updates.json`):

## Suggested Skill Updates (N issues found)

| #   | Severity        | File     | Issue               | Fix Prompt                                                                           |
| --- | --------------- | -------- | ------------------- | ------------------------------------------------------------------------------------ |
| 1   | <emoji> <level> | `<file>` | <short description> | <details><summary>Copy</summary><br>`<full droid prompt to fix the issue>`</details> |

Severity: `🔴 Breaking` (fails every run), `🟡 Degraded` (intermittent/suboptimal), `🔵 Info` (new knowledge). Each Fix Prompt must be a self-contained instruction Droid can execute when pasted, naming the target file and the "Known Failure Modes" section to append to.

Do NOT suggest updates already covered in Known Failure Modes. If there are no genuinely new insights, omit the section entirely.

If `failure_learning` is `auto_commit` or `open_pr`, also write `qa-results/skill-updates.json` as an array of `{file, section, action: append|replace, content}` objects; the workflow applies it.
