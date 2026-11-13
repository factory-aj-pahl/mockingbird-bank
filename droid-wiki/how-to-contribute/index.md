# How to contribute

Mockingbird Bank is a solo-maintained repository with a small, strict process: work starts from a Linear issue, lands through a pull request that must pass five required status checks, and counts as done only when `./gradlew build` is green. This section covers that process and the tools behind it.

## Pages in this section

| Page | What it covers |
| --- | --- |
| [Development workflow](development-workflow.md) | Branch, commit, PR, CI, merge and what runs on `master` afterwards |
| [Testing](testing.md) | Test layers, how to run them, retries, timings, coverage gates, known gaps |
| [Functional QA](functional-qa.md) | The Droid `qa` skill, its flows and targets, and the PR workflow that posts a QA Report |
| [Debugging](debugging.md) | Logs, correlation IDs, Actuator endpoints, JFR profiling, common errors |
| [Patterns and conventions](patterns-and-conventions.md) | Code layout, DI, entity and service rules, comment style |
| [Tooling](tooling.md) | Gradle plugins and tasks, scripts, dev container, every GitHub workflow |

New to the repo? Start with [Getting started](../overview/getting-started.md), then come back here.

## Picking up work

The backlog lives in Linear, in the project "Mockingbird Bank" (team "SE Demo", issue key `SED`). `AGENTS.md` links to it under "Project management". Pick an issue there and carry its key through the work:

- Put the key in the branch name, for example a branch for `SED-123`.
- Put it in the PR description, and in the commit subject when the change maps to one issue. Recent history shows the pattern: `Add show/hide eye toggles for full account numbers (SED-649) (#1)`.

GitHub issues are also accepted. `.github/ISSUE_TEMPLATE/` holds two forms and a config:

| Template | Labels applied | Notes |
| --- | --- | --- |
| `.github/ISSUE_TEMPLATE/bug_report.yml` | `bug`, `triage` | Asks for repro steps, commit or image tag, where it ran, and logs from `build/logs/analytics-events.log` |
| `.github/ISSUE_TEMPLATE/feature_request.yml` | `feature`, `triage` | Asks whether UI-facing work should ship behind a feature flag, and confirms the change stays read-only |
| `.github/ISSUE_TEMPLATE/config.yml` | n/a | Disables blank issues and sends suspected vulnerabilities to a private GitHub security advisory |

## Definition of done

A change is ready to merge when every box in `.github/pull_request_template.md` is honestly ticked:

- [ ] `./gradlew build` passes locally. That one command compiles, runs every test (Docker required), and runs Spotless, Checkstyle, PMD, CPD, JaCoCo thresholds, `buildHealth`, Javadoc and `validateAgentsMd`.
- [ ] New or changed behavior has tests, or the PR explains the gap.
- [ ] `AGENTS.md` is updated if the change affects how to build, run, test or deploy. The `validateAgentsMd` task (and the pre-commit hook) fail if `AGENTS.md` names a Gradle task or repo path that no longer exists.
- [ ] UI-facing changes that are not plain bug fixes sit behind a feature flag. See [Feature flags](../features/feature-flags.md).
- [ ] No secrets, real customer data or PII in code, migrations, fixtures or logs.

The template also asks "How was this verified?" and expects more than "should work" for UI or infra changes: say whether you ran `./scripts/run-interactive-qa.sh`, ran `/qa`, or clicked through against a real Postgres. For PRs that touch app code, the "QA Report" comment from `.github/workflows/qa.yml` is extra evidence, not a substitute.

## Review and merge gates

| Gate | Source | Effect |
| --- | --- | --- |
| Pull request required | `Protect Master` ruleset on `master` | Every change lands through a PR. The ruleset has no bypass actors, so admins cannot push to `master` directly either |
| Required status checks | `Protect Master` ruleset | Five checks must pass: `Build and test` and `Fast checks (lint, static analysis)` from `.github/workflows/ci.yml`, `Analyze (java-kotlin)` from `.github/workflows/codeql.yml`, `droid-review` from `.github/workflows/droid-review.yml`, and `Functional QA (Droid)` from `.github/workflows/qa.yml` |
| Up to date with `master` | `Protect Master` ruleset | A PR branch that is behind `master` must be updated, and its checks run again, before it can merge |
| No force-push or deletion of `master` | `Protect Master` ruleset | No exceptions |
| Owner review | `.github/CODEOWNERS` (`* @ajpahl1008`) | GitHub requests the owner's review on every path. The ruleset requires no approvals, so this does not block merge |
| Secret scan | `.github/workflows/secret-scan.yml` | gitleaks fails its check on a leaked credential. It is not one of the five required checks |
| Automated Droid review | `.github/workflows/droid-review.yml` | Required. Factory Droid posts a code review and a security review on every non-draft PR, and again on every push to it |
| Droid functional QA | `.github/workflows/qa.yml` | Required, but the job succeeds whatever QA finds, so a FAIL row does not block merge. Read the sticky "QA Report" comment, not the check status (see [Functional QA](functional-qa.md#the-qa-workflow)) |

The ruleset requires neither approvals nor conversation resolution, so open review threads do not block merge. `AGENTS.md` describes the ruleset under "Security & CI"; the ruleset itself lives in the repository settings and is not visible in the code. A job that a workflow `if:` skips reports success, so drafts, forks and Dependabot PRs pass the QA check without a QA run.

Mentioning `@droid` in an issue, PR body or review comment triggers `.github/workflows/droid.yml` for an ad hoc run. Both Droid workflows, the QA workflow and the wiki refresh in `.github/workflows/droid-wiki-refresh.yml` need the `FACTORY_API_KEY` secret. The wiki refresh opens one rolling "docs: refresh wiki" PR, which stays open and is never merged (see [Auto-merge](#auto-merge)).

### Auto-merge

The "Pull requests" section at the end of `AGENTS.md` (added in PR #13 and extended in PR #14, both 2026-11-07) says when to turn on GitHub auto-merge. Auto-merge only queues the PR; it still merges only after every gate above passes.

| PR kind | When to enable auto-merge |
| --- | --- |
| Non-feature PR into `master` (fix, chore, docs, dependencies) | Right after opening it: `gh pr merge <PR number> --auto --squash` |
| Feature PR tracked in Linear | Only after someone newly applies the `approved` label to authorize a version-only bump, and that bump has been pushed to the PR |
| The rolling "docs: refresh wiki" PR from `.github/workflows/droid-wiki-refresh.yml` | Never. Keep it open; the next refresh updates it in place. Its body says the same: "Keep this rolling pull request open; the next refresh updates it in place. Do not merge it." See [Wiki refresh](tooling.md#wiki-refresh) |

Never merge directly or bypass required checks. The ruleset has no bypass actors, so GitHub enforces this for admins too. No workflow applies or checks the `approved` label, and `AGENTS.md` does not say which file the version-only bump edits. In practice it is `baseVersion` in `gradle.properties`, the only version number the build reads: the first feature PR merged under this rule, #16 (SED-791, `showAccountCount`), ended with a "Bump baseVersion to 0.2.0" commit that changed only that line. #17 (SED-847, account programs) did the same with "Bump baseVersion to 0.3.0", the current value (see [Build version](../features/build-version.md)).

## Labels

Labels drive release notes and backlog grooming, so they matter more than usual:

- `area: ui`, `area: security`, `area: database`, `area: ci`, `area: docs`, `area: build`, `area: tests` are applied automatically by `.github/workflows/labeler.yml` from the paths a PR touches (rules in `.github/labeler.yml`). Do not set them by hand.
- `feature`/`enhancement`, `fix`/`bug`, `security`, `dependencies`, `chore`/`maintenance`/`documentation` decide the release-notes section. `major`/`minor`/`patch` steer the next version number, and `skip-changelog` drops a PR from the notes. See [Development workflow](development-workflow.md#release-notes).
- `priority: high`, `pinned` and `security` exempt an issue or PR from the stale bot in `.github/workflows/stale.yml`.
- `approved` is applied by hand to a Linear-tracked feature PR to authorize its version-only bump, which unlocks auto-merge (see [Auto-merge](#auto-merge)). It is not in the label list in `AGENTS.md` and no workflow reads it.
