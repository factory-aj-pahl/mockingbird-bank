# Alerting

The only automated alerts are two Slack messages to `#mbb-ops`, both from GitHub Actions on pushes to `master`. Nothing watches the running app. A database outage, an error spike or an open circuit breaker in LAB is noticed by a person, not paged.

## Slack alerts

Both alerts post to the incoming webhook stored in the `SLACK_ALERTS_WEBHOOK_URL` repository secret, which `AGENTS.md` says posts to `#mbb-ops`. Each builds a JSON body with `jq` and sends it with `curl -sf`.

| Alert | Workflow and job | Fires when | Message |
| --- | --- | --- | --- |
| CI failed on master | `.github/workflows/ci.yml`, job `notify-on-failure` | A `push` to `master` where `fast-checks` or `test` ended with `failure` | ":red_circle: CI failed on master (job: `fast-checks` or `test`)", run link, commit SHA, actor |
| Published image unhealthy | `.github/workflows/publish-image.yml`, last step of `deployment-smoke-test` | Any earlier step in that job failed, usually the image never reporting `"status":"UP"` within 30 tries 2 seconds apart | ":red_circle: Published image failed its post-deploy health check", run link, image tag |

Details:

- `notify-on-failure` checks `github.event_name == 'push'`, so pull request failures never alert. Those show up as PR checks.
- CI uses `concurrency` with `cancel-in-progress: true`. A run cancelled by a newer push is `cancelled`, not `failure`, and does not alert.
- If `test` failed, the message names `test` even when `fast-checks` also failed.
- The smoke test boots the pushed `sha-<commit>` image against a throwaway `postgres:16-alpine` service container on the runner. It is not the LAB deployment. The job also records a GitHub Deployment in environment `production` and marks it `success` or `failure`.
- If the `publish` job itself fails (image build or push to GHCR), `deployment-smoke-test` is skipped and no Slack message is sent.

### Without the secret

Both steps start with:

```bash
if [ -z "${SLACK_WEBHOOK_URL:-}" ]; then
  echo "SLACK_ALERTS_WEBHOOK_URL secret not set - skipping Slack notification." >&2
  exit 0
fi
```

Forks and repos without the secret get a log line and a green step. If the secret is set but the webhook call fails, `curl -sf` under `set -euo pipefail` fails the step.

## Signals that are not alerts

| Source | What happens | Where to look |
| --- | --- | --- |
| DAST (`.github/workflows/dast.yml`) | ZAP baseline scan of `/login` on every `master` push, Mondays at 04:23 UTC, and on demand. `fail_action: false` and `allow_issue_writing: false`, so findings never fail the job, open issues or post to Slack. | `zap-baseline-report` artifact (HTML, Markdown, JSON), kept 30 days |
| GitHub Deployments | Success or failure per published image | Repo Deployments tab, or `gh api repos/<owner>/<repo>/deployments` |
| CodeQL, gitleaks | Normal check results; a failure on `master` is not sent to Slack | Security tab, Actions tab |
| Backlog grooming (`.github/workflows/stale.yml`) | Daily at 06:00 UTC: issues and PRs idle for 60 days get a `stale` label and a comment, then close 14 days later. `priority: high`, `pinned` and `security` labels and draft PRs are exempt. | Comments on the issue or PR |
| Build performance | `build.gradle` warns, without failing, when a build takes over 5 minutes | `build-performance-*` CI artifacts |
| Flaky tests | Retried tests that passed are listed, not failed | `flaky.txt` in the `test-report` artifact |

## Alerts and runbooks

| Situation | Alert today | Runbook |
| --- | --- | --- |
| CI failing on `master` | Slack, `ci.yml` | None; fix or revert the commit. `docs/runbooks/rollback.md` step 4 covers reverting. |
| Published image fails to boot or become healthy | Slack, `publish-image.yml` | `docs/runbooks/app-wont-start.md` |
| Image build or push fails | None | None |
| LAB pod crash-looping | None | `docs/runbooks/app-wont-start.md` |
| `/actuator/health` DOWN, database unreachable | None | `docs/runbooks/database-down.md` |
| Circuit breaker open (`ServiceUnavailableException`) | None | `docs/runbooks/database-down.md` |
| Spike in `outcome=error` events | None | `docs/runbooks/error-spike.md` |
| Bad release already running in LAB | None | `docs/runbooks/rollback.md` |
| DAST finding | None (artifact only) | None; triage the report by hand |

`docs/runbooks/README.md` says each runbook assumes you start "from a Slack alert in `#mbb-ops`", but only the image smoke test row has an alert that leads to a runbook.

## Gaps

- No runtime alerting. Nothing evaluates `product.events`, `resilience4j.circuitbreaker.state` or the health endpoint on a schedule. The metrics are not even readable over HTTP yet (see [Metrics](metrics.md)).
- No alert on the LAB rollout. The Harness pipeline that deploys `k8s/*.yaml` lives outside this repo, and the readiness probe in `k8s/03-app.yaml` only keeps traffic away from an unready pod.
- No log-based alerting. The analytics log sits on the pod filesystem and the console log is only read with `kubectl logs`; no shipper is configured.
- Security scan failures and DAST findings on `master` do not reach Slack.

The cheapest first step would be exposing metrics (see [Metrics](metrics.md#option-2-add-a-scraping-or-push-registry)) and alerting on `resilience4j.circuitbreaker.state{state="open"}` and the rate of `product.events{outcome="error"}`, which line up with the database-down and error-spike runbooks. The [Monitoring](index.md) page has the manual health checklist to use until then.

## Key source files

| File | Purpose |
| --- | --- |
| `.github/workflows/ci.yml` | `notify-on-failure` Slack alert |
| `.github/workflows/publish-image.yml` | Smoke test, GitHub Deployment, Slack alert |
| `.github/workflows/dast.yml` | ZAP baseline, report artifact |
| `.github/workflows/stale.yml` | Backlog grooming |
| `docs/runbooks/` | Runbooks the alerts point to |
