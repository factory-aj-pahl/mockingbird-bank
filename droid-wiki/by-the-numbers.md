# By the numbers

Data collected on 2026-11-04.

A small codebase that grew fast. These figures come from `git ls-files`, `wc -l` and `git log` on `master` at commit `2b3e914`, so they describe tracked files only (no build output, no `node_modules`).

## Size

The repository tracks 124 files and 8,404 lines, excluding three generated or binary files: `gradle/wrapper/gradle-wrapper.jar`, `qa/package-lock.json` and `.devcontainer/devcontainer-lock.json`.

```mermaid
xychart-beta horizontal
    title "Lines by language"
    x-axis ["Java", "YAML", "Markdown", "Gradle", "Shell", "XML", "TypeScript", "SQL", "Batch", "zsh", "JSON", "CSS", "Other"]
    y-axis "Lines" 0 --> 3600
    bar [3484, 1687, 1089, 693, 404, 175, 123, 97, 82, 58, 55, 47, 410]
```

| Language | Files | Lines | Notes |
|---|---|---|---|
| Java | 51 | 3,484 | 34 main (2,158 lines), 17 test (1,326 lines) |
| YAML (`.yml` + `.yaml`) | 31 | 1,687 | 12 GitHub workflows, Kubernetes manifests, `application.yml` |
| Markdown | 10 | 1,089 | `AGENTS.md` alone is 573 lines |
| Gradle | 2 | 693 | `build.gradle` is 692 of them |
| Shell (`.sh`) | 5 | 404 | Everything under `scripts/` |
| XML | 3 | 175 | Checkstyle, PMD and Logback configs |
| TypeScript | 2 | 123 | The Playwright suite in `qa/` |
| SQL | 2 | 97 | The Flyway migration and `scripts/bootstrap-lab-db.sql` |
| Batch | 1 | 82 | `gradlew.bat` |
| zsh | 3 | 58 | The Docker image scripts at the repo root |
| JSON | 3 | 55 | |
| CSS | 1 | 47 | `src/main/frontend/themes/mockingbird/styles.css` |
| Other | 10 | 410 | `.properties` files, `gradlew`, Dockerfiles, dotfiles, the pre-commit hook |

### File counts by role

| Role | Count | What it includes |
|---|---|---|
| Application source | 36 | 34 Java files under `src/main/java`, the theme CSS, the Flyway migration |
| Tests | 19 | 17 JUnit classes under `src/test/java`, 2 Playwright files under `qa/` |
| Configuration | 39 | 31 YAML files, 3 XML rule/logging configs, 3 JSON files, 2 `.properties` files |

The test-to-code ratio for Java is 0.61 (1,326 test lines per 2,158 main lines). Coverage gates are described in [testing](how-to-contribute/testing.md).

## Activity

The whole history is 40 commits between 2026-09-26 and 2026-11-04. Almost all of it landed in the first three days; see [lore](lore.md) for what each burst was about.

| ISO week | Dates | Commits |
|---|---|---|
| 2026-W39 | Sep 21-27 | 25 |
| 2026-W40 | Sep 28-Oct 4 | 9 |
| 2026-W41 | Oct 5-11 | 0 |
| 2026-W42 | Oct 12-18 | 0 |
| 2026-W43 | Oct 19-25 | 1 |
| 2026-W44 | Oct 26-Nov 1 | 0 |
| 2026-W45 | Nov 2-8 | 5 |

```mermaid
xychart-beta
    title "Commits per ISO week"
    x-axis ["W39", "W40", "W41", "W42", "W43", "W44", "W45"]
    y-axis "Commits" 0 --> 26
    bar [25, 9, 0, 0, 1, 0, 5]
```

By day, 2026-09-27 alone has 24 commits, 2026-09-28 has 8, and 2026-11-04 has 4.

### Churn hotspots

Files touched by the most commits (`git log --name-only`):

| File | Commits |
|---|---|
| `build.gradle` | 14 |
| `AGENTS.md` | 14 |
| `src/main/resources/application.yml` | 7 |
| `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | 7 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | 6 |
| `src/main/java/com/mockingbirdbank/service/AccountService.java` | 5 |
| `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` | 5 |
| `src/main/java/com/mockingbirdbank/ui/layout/MainLayout.java` | 5 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountCard.java` | 5 |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | 5 |
| `.gitignore` | 5 |

`build.gradle` and `AGENTS.md` tie for the lead because nearly every tooling change added a Gradle task and a matching paragraph of documentation. `scripts/validate-agents-md.sh`, wired into `check` as `validateAgentsMd`, fails the build if `AGENTS.md` mentions a Gradle task or file path that no longer exists (see [tooling](how-to-contribute/tooling.md)).

### Bot-attributed commits

| Kind | Commits | Share of 40 |
|---|---|---|
| Carries a `Co-authored-by: factory-droid[bot]` trailer | 33 | 82.5% |
| Authored by `dependabot[bot]` | 1 | 2.5% |
| Either of the above | 34 | 85.0% |

Treat these as lower bounds. They only count commits where the trailer or bot author was recorded, and any agent help without a trailer is invisible to `git log`. The single Dependabot commit is `77317b7` (2026-09-28), which bumped Playwright in `qa/` from 1.49.1 to 1.63.0.

## Complexity

Main Java code, grouped by package under `src/main/java/com/mockingbirdbank/`:

| Package | Files | Lines | Average lines per file |
|---|---|---|---|
| `ui/view` | 5 | 675 | 135 |
| `service` | 2 | 172 | 86 |
| `ui/layout` | 2 | 161 | 81 |
| `observability` | 3 | 212 | 71 |
| `config` | 4 | 254 | 64 |
| `analytics` | 3 | 188 | 63 |
| `config/logging` | 1 | 53 | 53 |
| `model` | 5 | 247 | 49 |
| `security` | 2 | 86 | 43 |
| `resilience` | 2 | 59 | 30 |
| (root, `Application.java`) | 1 | 15 | 15 |
| `repository` | 4 | 36 | 9 |

The Vaadin views are the heaviest code because each one builds its layout, styling and event wiring in Java. Repositories are one-method Spring Data interfaces. See [UI](systems/ui.md) and [persistence](systems/persistence.md).

### Largest files

| File | Lines |
|---|---|
| `build.gradle` | 692 |
| `AGENTS.md` | 573 |
| `gradlew` | 248 |
| `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | 239 |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | 208 |
| `.github/workflows/publish-image.yml` | 206 |
| `scripts/k3d-deploy.sh` | 170 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | 169 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountNumberDisplay.java` | 149 |
| `.github/workflows/ci.yml` | 148 |

The build script is longer than any Java class: it carries the quality gates, coverage rules, test retry and timing reports, git metadata, the `installGitHooks` task, and the build-performance and code-quality summaries.

### Debt markers

There are zero `TODO` or `FIXME` comments in tracked code. Checkstyle is configured to report any such marker as a warning (`config/checkstyle/checkstyle.xml`), so the count is checked on every build.
