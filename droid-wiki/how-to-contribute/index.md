# How to contribute

Mockingbird Bank is a solo-maintained repository with a small, strict process: work starts from a Linear issue, lands through a pull request that must pass CI and owner review, and counts as done only when `./gradlew build` is green. This section covers that process and the tools behind it.

## Pages in this section

| Page | What it covers |
| --- | --- |
| [Development workflow](development-workflow.md) | Branch, commit, PR, CI, merge and what runs on `master` afterwards |
| [Testing](testing.md) | Test layers, how to run them, retries, timings, coverage gates, known gaps |
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

The template also asks "How was this verified?" and expects more than "should work" for UI or infra changes: say whether you ran `./scripts/run-interactive-qa.sh` or clicked through against a real Postgres.

## Review and merge gates

| Gate | Source | Effect |
| --- | --- | --- |
| Owner review | `.github/CODEOWNERS` (`* @ajpahl1008`) | Every path needs the owner's approval |
| CI status check | `.github/workflows/ci.yml` | Branch protection on `master` requires it to pass |
| Conversation resolution | Branch protection (per `AGENTS.md`) | Open review threads block merge |
| No force-push or deletion of `master` | Branch protection | Admins can still bypass for direct pushes |
| Secret scan | `.github/workflows/secret-scan.yml` | gitleaks fails the PR check on a leaked credential |
| Automated Droid review | `.github/workflows/droid-review.yml` | Factory Droid posts a code review and a security review on every non-draft PR |

Mentioning `@droid` in an issue, PR body or review comment triggers `.github/workflows/droid.yml` for an ad hoc run. Both Droid workflows, and the wiki refresh in `.github/workflows/droid-wiki-refresh.yml`, need the `FACTORY_API_KEY` secret. The wiki refresh opens its own "docs: refresh wiki" PR, which goes through the same gates as any other PR.

## Labels

Labels drive release notes and backlog grooming, so they matter more than usual:

- `area: ui`, `area: security`, `area: database`, `area: ci`, `area: docs`, `area: build`, `area: tests` are applied automatically by `.github/workflows/labeler.yml` from the paths a PR touches (rules in `.github/labeler.yml`). Do not set them by hand.
- `feature`/`enhancement`, `fix`/`bug`, `security`, `dependencies`, `chore`/`maintenance`/`documentation` decide the release-notes section. `major`/`minor`/`patch` steer the next version number, and `skip-changelog` drops a PR from the notes. See [Development workflow](development-workflow.md#release-notes).
- `priority: high`, `pinned` and `security` exempt an issue or PR from the stale bot in `.github/workflows/stale.yml`.
