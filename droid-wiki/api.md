# API

Mockingbird Bank has no REST or JSON API for its UI. The screens are Vaadin Flow views rendered on the server, and the browser talks to them over Vaadin's own UIDL request channel, so the only HTTP surface meant to be called directly is a handful of Spring Boot Actuator endpoints. This page lists the Vaadin routes, the Actuator endpoints, and the one custom header the app reads and writes.

For how access rules are enforced, see [Security](security.md). For the code behind the custom endpoints, see [Observability](systems/observability.md) and [Feature flags](features/feature-flags.md).

## Vaadin routes

These are browser pages, not API endpoints. Each one is a Java class with a `@Route` annotation; Vaadin handles everything after the first page load over its internal channel.

| Path | View | Access | Notes |
| --- | --- | --- | --- |
| `/login` | `src/main/java/com/mockingbirdbank/ui/view/LoginView.java` | Public (`@AnonymousAllowed`) | `LoginOverlay` that posts `username` and `password` to `/login`. `?error` shows the error state. See [Sign-in](features/sign-in.md). |
| `/` and `/accounts` | `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | Signed in (`@PermitAll`) | `@Route("accounts")` plus `@RouteAlias("")`. See [Dashboard](features/dashboard.md). |
| `/accounts/{id}` | `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | Signed in (`@PermitAll`) | Same `accounts` path, told apart by the `HasUrlParameter<String>` parameter (one path segment), which the view parses into an account ID. Only the signed-in holder's own accounts; another holder's account ID gets the not-found page, see [the ownership check](security.md#account-detail-ownership-check). See [Account detail](features/account-detail.md). |

Both signed-in views render inside `src/main/java/com/mockingbirdbank/ui/layout/MainLayout.java`, which carries its own `@PermitAll`. An anonymous request to either redirects to `/login` (asserted by `ApplicationSmokeTest.unauthenticatedRequestToAccountsRedirectsToLogin`). An unknown account ID, and a non-numeric one such as `/accounts/abc`, ends in Vaadin's not-found page.

Sign out has no route of its own. The Sign out button in `MainLayout` calls Vaadin's `AuthenticationContext.logout()`.

## Actuator endpoints

`src/main/resources/application.yml` exposes exactly four endpoints over HTTP:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,featureflags,profiling
```

`src/main/java/com/mockingbirdbank/security/SecurityConfig.java` then decides who may call them:

| Endpoint | Methods | Access | Source |
| --- | --- | --- | --- |
| `/actuator/health` and `/actuator/health/**` | GET | Public | Spring Boot Actuator |
| `/actuator/info` | GET | Public | Spring Boot Actuator, limited to build and git info |
| `/actuator/featureflags` | GET | Signed-in session | `src/main/java/com/mockingbirdbank/config/FeatureFlagsEndpoint.java` |
| `/actuator/profiling` | GET, POST, DELETE | Signed-in session | `src/main/java/com/mockingbirdbank/observability/ProfilingEndpoint.java` |

Any signed-in user can call the two authenticated endpoints. There is only one role (`ROLE_USER`), so there is no separate operator role.

### `GET /actuator/health`

Standard Actuator health, including the database check. It is public because probes and smoke checks call it without a session:

| Caller | Path |
| --- | --- |
| LAB readiness probe, `k8s/03-app.yaml` | `/actuator/health` |
| k3d startup and liveness probes, `k8s/local/app.yaml` | `/actuator/health/liveness` |
| k3d readiness probe, `k8s/local/app.yaml` | `/actuator/health` (includes the DB, so traffic stops when Postgres goes away) |
| `.github/workflows/dast.yml`, `.github/workflows/publish-image.yml`, `.github/workflows/qa.yml`, `scripts/run-interactive-qa.sh` | `/actuator/health`, waiting for `"status":"UP"` |

The `liveness` and `readiness` groups exist in the k3d deploy because `k8s/local/kustomization.yaml` sets `MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED=true`. `application.yml` does not set it.

```bash
curl -s localhost:8080/actuator/health
# {"status":"UP", ...}
```

### `GET /actuator/info`

Public, so "which build is deployed" needs no login. `application.yml` turns off every info contributor (`management.info.defaults.enabled: false`) and turns back on only `build` and `git` in `simple` mode. `ApplicationSmokeTest.infoEndpointIsPublicAndReportsOnlyBuildAndGitBasics` asserts that `build.version`, `git.commit.id` and `git.branch` are present and that `java`, `os` and `process` are not.

Example for a build of commit `78a2f03` with `baseVersion=0.3.0`:

```bash
curl localhost:8080/actuator/info
# {"build":{"version":"0.3.0-78a2f03",...},"git":{"branch":"master","commit":{"id":"78a2f03",...}}}
```

See [Build version](features/build-version.md) for where these values come from.

### `GET /actuator/featureflags`

Read-only. Returns `FeatureFlagService.allFlags()`, a map of flag name to boolean:

```json
{
  "showAccountNumberOnDashboard": true,
  "showWelcomeBanner": false,
  "allowAccountNumberReveal": true,
  "showBuildVersion": true,
  "showAccountCount": false,
  "showAccountPrograms": false
}
```

The values shown are the defaults from `application.yml`. Exact key names come from `src/main/java/com/mockingbirdbank/config/FeatureFlagService.java`; see [Feature flags](features/feature-flags.md).

### `/actuator/profiling`

Starts and stops a JDK Flight Recorder recording on the instance that receives the request. At most one recording runs at a time.

| Request | Response body |
| --- | --- |
| `GET` with nothing running | `{"recording": false}` |
| `GET` while recording | `{"recording": true, "name": "mockingbird-bank-<epoch millis>", "startTime": "<Instant>"}` |
| `POST` | `{"started": true, "name": "mockingbird-bank-<epoch millis>", "file": "<output-dir>/mockingbird-bank-<epoch millis>.jfr"}` |
| `POST` while already recording | `{"error": "A recording is already running - stop it first."}` |
| `DELETE` | `{"stopped": true, "file": "<output-dir>/mockingbird-bank-<epoch millis>.jfr"}` |
| `DELETE` with nothing running | `{"error": "No recording is running."}` |

The two `error` cases are ordinary return values, so they come back as HTTP 200. Check the body, not the status code. Only a failure to create the output directory or load the JFR configuration throws (`IllegalStateException`), which surfaces as a server error.

The output directory is `mockingbird.profiling.output-dir`, default `${java.io.tmpdir}/mockingbird-bank-jfr`. The file stays on that pod's filesystem, so copy it out (for example with `kubectl cp`) before the pod restarts. More detail in [Observability](systems/observability.md).

### Calling the authenticated endpoints

There are no API tokens or HTTP Basic auth. The only credential the app accepts is the session cookie that form login creates. Two things make scripting this awkward:

- **Logging in with curl.** `SecurityConfig` calls `VaadinSecurityConfigurer.loginView(LoginView.class)`, which exempts the configured `/login` URL from Spring Security's CSRF checks. A username/password form `POST` to `/login` therefore does not need a CSRF token. Keep the session cookie returned after a successful login and send it on later requests. The tests use Spring Security Test's `formLogin()` helper, which adds a token anyway, so they do not show whether one is required.
- **POST and DELETE.** The exemption covers only the login URL. `SecurityConfig` leaves CSRF protection on for everything else, so `POST` and `DELETE /actuator/profiling` need a valid CSRF token as well as the session cookie.

For reads, you can also sign in with a browser, copy the `JSESSIONID` cookie from the developer tools, and send it with curl:

```bash
curl -s -b 'JSESSIONID=<value from the browser>' localhost:8080/actuator/featureflags
curl -s -b 'JSESSIONID=<value from the browser>' localhost:8080/actuator/profiling
```

Without a valid session, expect a redirect to `/login` rather than a JSON 401. The CSRF behavior above follows from the Vaadin and Spring Security configuration; it has not been checked against a running instance, and there is no documented recipe in the repo for starting a recording from a script.

### What is not exposed

`metrics`, `prometheus`, `env`, `beans`, `loggers` and every other built-in Actuator endpoint are left out of `management.endpoints.web.exposure.include`. In particular, `/actuator/metrics/product.events` is not reachable, even though `AGENTS.md`, the `AnalyticsEventService` Javadoc, a comment in `application.yml`, and `docs/runbooks/error-spike.md` all describe it as available. The counters are still recorded in the in-process Micrometer registry. See [Metrics](how-to-monitor/metrics.md).

## `X-Correlation-Id` header

`src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` runs first on every request (`Ordered.HIGHEST_PRECEDENCE`), ahead of Spring Security, so it applies to Vaadin pages, Vaadin's internal requests and Actuator calls alike.

| Direction | Behavior |
| --- | --- |
| Request | If `X-Correlation-Id` is present and not blank, its value is used as-is. Otherwise the filter generates a random UUID. |
| Response | The same value is always echoed back in `X-Correlation-Id`. |
| Logs | The value is put in SLF4J MDC as `correlationId` for the request, appears in every log line through the `logging.pattern.level` setting, and is added to analytics event lines as `correlationId=...`. |

```bash
curl -si -H 'X-Correlation-Id: support-ticket-42' localhost:8080/actuator/health | grep -i x-correlation-id
# X-Correlation-Id: support-ticket-42
```

The incoming value is not checked for length or characters. See [Security](security.md#request-headers-and-usernames-in-logs) and [Logging](how-to-monitor/logging.md).

## Key source files

| File | Purpose |
| --- | --- |
| `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` | Actuator access rules, Vaadin security setup |
| `src/main/resources/application.yml` | Endpoint exposure, info contributors |
| `src/main/java/com/mockingbirdbank/config/FeatureFlagsEndpoint.java` | `/actuator/featureflags` |
| `src/main/java/com/mockingbirdbank/observability/ProfilingEndpoint.java` | `/actuator/profiling` |
| `src/main/java/com/mockingbirdbank/observability/CorrelationIdFilter.java` | `X-Correlation-Id` |
| `src/main/java/com/mockingbirdbank/ui/view/LoginView.java` | `/login` |
| `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | `/`, `/accounts` |
| `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | `/accounts/{id}` |
| `src/test/java/com/mockingbirdbank/ApplicationSmokeTest.java` | Health, info, redirect and login assertions |
