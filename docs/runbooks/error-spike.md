# Runbook: Error spike / elevated failure rate

Symptoms: more failed logins, 404'd accounts, or unhandled UI exceptions than usual - noticed via
`/actuator/metrics/product.events`, a spike in `build/logs/analytics-events.log`, or just users
reporting things breaking.

## 1. Quantify it before chasing it

```bash
curl -s 'http://<service-ip>/actuator/metrics/product.events?tag=outcome:error' | jq
```

`AnalyticsEventService` tags every tracked event with `outcome` (`success`/`error`) and, for
errors, a short `reason` - so you can immediately see *which* kind of error is spiking without
guessing:

```bash
curl -s 'http://<service-ip>/actuator/metrics/product.events?tag=outcome:error&tag=event:login' | jq
curl -s 'http://<service-ip>/actuator/metrics/product.events?tag=outcome:error&tag=event:account.viewed' | jq
```

## 2. Find one concrete failing request

Every log line during a request carries the same `correlationId` (see
`com.mockingbirdbank.observability.CorrelationIdFilter`) and, if distributed tracing is wired to
a real collector, the same `traceId`/`spanId` (see [Distributed
tracing](../../AGENTS.md#observability--resilience)). Grep `analytics-events.log` or the regular
application log for one occurrence of the spiking error, then pull every line sharing that
`correlationId`:

```bash
grep 'correlationId=<the-id>' /path/to/application.log
```

If tracing is pointed at a real backend (Jaeger/Tempo/etc.), look up the same `traceId` there to
see exactly which downstream call (database query, circuit breaker trip) the request spent its
time on or failed at.

## 3. Common root causes for this app specifically

| What's spiking | Likely cause | Where to look |
|---|---|---|
| `login` errors (`outcome:error`) | Bad credentials being retried (real user issue, or a scripted attack), or the seeded demo password (`MOCKINGBIRD_DEV_PASSWORD`) rotated without updating whoever's testing | `AuthenticationEventListener`; if this is unexpectedly high-volume from one source, this is a security question, not just an ops one - see `SECURITY.md`. |
| `account.viewed` errors | A stale/deep link to a deleted or reassigned account ID, or a real bug in the account-ownership check in `AccountService` | The `reason` tag on the metric, plus `AccountDetailView`'s call site. |
| `ui.unhandled_exception` (from `GlobalErrorHandler`) | A genuine unhandled bug in a view, tagged by exception type | The `reason` tag is the exception's simple class name - that alone usually narrows it to one view/service. |
| `programs.unavailable` (from `ProgramLookup`) | The dashboard or an account page showed "Programs can't be shown right now" instead of programs. `reason=ServiceUnavailableException` means the `programs` circuit breaker was open; it opens when at least half of the last 10 calls inside it failed, such as a broken program query, or when all 10 took longer than 60 seconds (Resilience4j's default slow-call limit). `CannotCreateTransactionException` means no connection could be taken from the pool; it comes from the `@Transactional` proxy around `ProgramService`, outside the breaker. Any other reason is a database exception, either from a query inside the program read or from the proxy's commit or rollback, which also runs outside the breaker; the stack trace shows which | The `reason` tag. For any reason other than `ServiceUnavailableException`, the `Programs could not be read` WARN line (with stack trace) from `ProgramLookup` for the same correlation ID. `ServiceUnavailableException` has no WARN line of its own: check `resilience4j.circuitbreaker.state` and `resilience4j.circuitbreaker.not.permitted.calls` for `name=programs`, and the earlier WARN lines for the calls that failed. If there are none, the calls were slow rather than failing: check `resilience4j.circuitbreaker.slow.calls` for `name=programs`. For `CannotCreateTransactionException`, check [Database down](./database-down.md). On the dashboard the accounts and balances have already loaded when this event fires; on an account page the transactions query runs after the program section and can still fail the page. A full database outage usually fails the page at `currentHolder()` before the program section is reached. |
| A spike with no correlationId pattern in common (many different requests, same shape of error) | Likely an infra issue upstream of the app logic entirely | Check [Database down](./database-down.md) and `/actuator/health` first. |

## 4. If it's a real bug

File it with `.github/ISSUE_TEMPLATE/bug_report.yml` (already asks for the correlation ID/trace
ID and reproduction steps) rather than just fixing it live - `master` is protected and requires
CI, so there's no "just push a hotfix directly" path here even under pressure, which is
intentional.
