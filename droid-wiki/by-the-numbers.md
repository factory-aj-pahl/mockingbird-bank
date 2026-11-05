# By the numbers

Data collected on 2026-11-05.

A small codebase that grew fast. These figures come from `git ls-files`, `wc -l` and `git log` on `master` at commit `36ef6ef`, so they describe tracked files only (no build output, no `node_modules`).

## Size

The repository tracks 181 files and 14,892 lines, excluding three generated or binary files: `gradle/wrapper/gradle-wrapper.jar`, `qa/package-lock.json` and `.devcontainer/devcontainer-lock.json`.

48 of those files (5,345 lines) are this wiki as committed at `36ef6ef`. Without `droid-wiki/`, the repository is 133 files and 9,547 lines. Since the previous snapshot at `f97b7a4`, two commits landed: `7474d45` (PR #7) refreshed seven wiki pages, and `36ef6ef` added the Droid functional QA setup, 9 new files and 1,139 added lines across `.factory/`, `.github/workflows/qa.yml`, `AGENTS.md` and `.gitignore` (see [functional QA](how-to-contribute/functional-qa.md)). No Java changed.

```mermaid
xychart-beta horizontal
    title "Lines by language"
    x-axis ["Markdown", "Java", "YAML", "Gradle", "Shell", "XML", "JSON", "SQL", "TypeScript", "Batch", "zsh", "CSS", "Other"]
    y-axis "Lines" 0 --> 7000
    bar [6911, 3484, 2211, 693, 404, 175, 150, 140, 123, 82, 58, 47, 414]
```

| Language | Files | Lines | Notes |
|---|---|---|---|
| Markdown | 60 | 6,911 | 47 wiki pages under `droid-wiki/` (5,263 lines). Of the other 13, `AGENTS.md` is 600 lines and the three QA skill files under `.factory/skills/` are 532 |
| Java | 51 | 3,484 | 34 main (2,158 lines), 17 test (1,326 lines) |
| YAML (`.yml` + `.yaml`) | 34 | 2,211 | 13 GitHub workflows, Kubernetes manifests, `application.yml`, the QA config |
| Gradle | 2 | 693 | `build.gradle` is 692 of them |
| Shell (`.sh`) | 5 | 404 | Everything under `scripts/` |
| XML | 3 | 175 | Checkstyle, PMD and Logback configs |
| JSON | 5 | 150 | Includes `droid-wiki/.wiki-meta.json` (82 lines) and `.factory/settings.json` |
| SQL | 4 | 140 | The Flyway migration, `scripts/bootstrap-lab-db.sql`, and the QA seed and cleanup scripts |
| TypeScript | 2 | 123 | The Playwright suite in `qa/` |
| Batch | 1 | 82 | `gradlew.bat` |
| zsh | 3 | 58 | The Docker image scripts at the repo root |
| CSS | 1 | 47 | `src/main/frontend/themes/mockingbird/styles.css` |
| Other | 10 | 414 | `.properties` files, `gradlew`, Dockerfiles, dotfiles, the pre-commit hook |

### File counts by role

| Role | Count | What it includes |
|---|---|---|
| Application source | 36 | 34 Java files under `src/main/java`, the theme CSS, the Flyway migration |
| Tests | 19 | 17 JUnit classes under `src/test/java`, 2 Playwright files under `qa/` |
| Configuration | 43 | 34 YAML files, 3 XML rule/logging configs, 4 JSON files, 2 `.properties` files |
| Agent skills | 7 | 3 `SKILL.md` files, the QA report template and 2 QA SQL files under `.factory/skills/`, plus `.factory/settings.json` (also counted as configuration) |
| Generated documentation | 48 | The 47 pages of this wiki plus `droid-wiki/.wiki-meta.json` |

The test-to-code ratio for Java is 0.61 (1,326 test lines per 2,158 main lines). Coverage gates are described in [testing](how-to-contribute/testing.md).

## Activity

The whole history is 44 commits between 2026-09-26 and 2026-11-05. Most of it landed in the first three days; see [lore](lore.md) for what each burst was about.

| ISO week | Dates | Commits |
|---|---|---|
| 2026-W39 | Sep 21-27 | 25 |
| 2026-W40 | Sep 28-Oct 4 | 9 |
| 2026-W41 | Oct 5-11 | 0 |
| 2026-W42 | Oct 12-18 | 0 |
| 2026-W43 | Oct 19-25 | 1 |
| 2026-W44 | Oct 26-Nov 1 | 0 |
| 2026-W45 | Nov 2-8 | 9 |

```mermaid
xychart-beta
    title "Commits per ISO week"
    x-axis ["W39", "W40", "W41", "W42", "W43", "W44", "W45"]
    y-axis "Commits" 0 --> 26
    bar [25, 9, 0, 0, 1, 0, 9]
```

By day, 2026-09-27 alone has 24 commits, 2026-09-28 has 8, 2026-11-04 has 7, and 2026-11-03 and 2026-11-05 have one each.

### Churn hotspots

Files outside `droid-wiki/` touched by the most commits (`git log --name-only`). No wiki page has more than two commits yet.

| File | Commits |
|---|---|
| `AGENTS.md` | 16 |
| `build.gradle` | 14 |
| `src/main/resources/application.yml` | 7 |
| `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | 7 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | 6 |
| `.gitignore` | 6 |
| `src/main/java/com/mockingbirdbank/service/AccountService.java` | 5 |
| `src/main/java/com/mockingbirdbank/security/SecurityConfig.java` | 5 |
| `src/main/java/com/mockingbirdbank/ui/layout/MainLayout.java` | 5 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountCard.java` | 5 |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | 5 |

`AGENTS.md` and `build.gradle` lead because nearly every tooling change added a Gradle task and a matching paragraph of documentation. The two most recent code commits, `f97b7a4` and `36ef6ef`, changed `AGENTS.md` but not `build.gradle`. `scripts/validate-agents-md.sh`, wired into `check` as `validateAgentsMd`, fails the build if `AGENTS.md` mentions a Gradle task or file path that no longer exists (see [tooling](how-to-contribute/tooling.md)).

### Bot-attributed commits

| Kind | Commits | Share of 44 |
|---|---|---|
| Carries a `Co-authored-by: factory-droid[bot]` trailer | 36 | 81.8% |
| Authored by `dependabot[bot]` | 1 | 2.3% |
| Either of the above | 37 | 84.1% |

Treat these as lower bounds. They only count commits where the trailer or bot author was recorded, and any agent help without a trailer is invisible to `git log`. The second wiki refresh PR (`7474d45`, #7) was generated by Droid in CI but carries only a human co-author trailer, so it is not counted. The single Dependabot commit is `77317b7` (2026-09-28), which bumped Playwright in `qa/` from 1.49.1 to 1.63.0.

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

Excluding `droid-wiki/`. The largest wiki page at `36ef6ef` is `droid-wiki/reference/configuration.md` at 224 lines, which would rank seventh.

| File | Lines |
|---|---|
| `build.gradle` | 692 |
| `AGENTS.md` | 600 |
| `.github/workflows/qa.yml` | 367 |
| `.factory/skills/qa-web/SKILL.md` | 349 |
| `gradlew` | 248 |
| `src/main/java/com/mockingbirdbank/ui/view/DashboardView.java` | 239 |
| `src/test/java/com/mockingbirdbank/service/AccountServiceTest.java` | 208 |
| `.github/workflows/publish-image.yml` | 206 |
| `scripts/k3d-deploy.sh` | 170 |
| `src/main/java/com/mockingbirdbank/ui/view/AccountDetailView.java` | 169 |

The build script is longer than any Java class: it carries the quality gates, coverage rules, test retry and timing reports, git metadata, the `installGitHooks` task, and the build-performance and code-quality summaries. The QA workflow is now the longest workflow by a wide margin, mostly shell for the scope gate, the fallback report, secret scrubbing and the sticky comment. The `qa-web` skill is longer than any Java class too; it is a test plan written as prose.

### Debt markers

There are zero `TODO` or `FIXME` comments in tracked code. Checkstyle is configured to report any such marker as a warning (`config/checkstyle/checkstyle.xml`), so the count is checked on every build.
