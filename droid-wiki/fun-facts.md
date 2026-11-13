# Fun facts

A few things about this repository that are more interesting than they first look. Each one was checked against the source or `git log`.

## Jordan Ellis works at Bespin Engineering

The seeded adult demo holder, Jordan Ellis, gets a $3,200.00 "Direct Deposit - Bespin Engineering" 1 and 14 days ago (`src/main/java/com/mockingbirdbank/config/DataInitializer.java:52-61`). The same eight sample rows are seeded into all three of Jordan's accounts, so the paycheck also shows up in both savings accounts. Riley Chen, the youth customer, gets an allowance instead. Bespin is presumably a nod to the gas giant with Cloud City in *The Empire Strikes Back*. The line has been there since the first commit on 2026-09-26, and the Playwright suite asserts on it (`qa/tests/dashboard.spec.ts:30`), so renaming the employer would break interactive QA. See [persistence](systems/persistence.md) for the rest of the seed data.

## The logo is SVG in a Java string

There is no image file for the bird in the top bar. `src/main/java/com/mockingbirdbank/ui/layout/BirdMark.java` extends Vaadin's `Html` component and builds a 48x48 SVG from one cubic-Bezier `<path>` filled `#EDE7D3`, plus a 1.6-pixel-radius `<circle>` for the eye in the navy `#12233F`. Its Javadoc says it is drawn inline "so it stays crisp on the dark top bar at any size."

## The build script is the biggest file in the repo

`build.gradle` is 695 lines, longer than `AGENTS.md` (650) and more than twice the largest Java class, `DashboardView.java` (317). It started at 53 lines in the scaffold and was changed in 15 of the 52 commits, second only to `AGENTS.md` (21). More than any other file, it holds the quality gates, test reporting, git metadata and build summaries. Details are in [tooling](how-to-contribute/tooling.md) and [by the numbers](by-the-numbers.md).

## The secret scanner caught a test, twice

The repo has had gitleaks running since 2026-09-27, and both of its hits so far were the project's own test fixtures:

- `0d6ece3` (2026-09-27): a fake token in `ScrubbingMessageConverterTest` looked like a generic API key. It got an inline `gitleaks:allow` marker so the test could keep a realistic value.
- `e1eaad1` (2026-09-28): the correlation ID `"test-correlation-id-123"` was put into MDC under `CorrelationIdFilter.MDC_KEY`, and the word "KEY" plus a long literal matched the same rule. It was shortened to `"test-id"`.

The second false positive also broke deployments. The failed Secret scan check made the GitHub Deployments API return 409 Conflict for `publish-image.yml`, which `979d4da` fixed by sending `required_contexts: []` (see [releases](deployment/releases.md)).

## Dark mode came from a chat message

The dark mode commit (`1b43df4`, 2026-10-01) quotes its own feature request in the commit message: a `#mbb-feedback` message saying "would be great if we had a dark mode button on your site." The quoted timestamp is 2026-10-01T13:10:22Z and the commit is dated 09:29:33 -0400 (13:29:33Z), so the feature shipped about 19 minutes after it was asked for. The toggle stores the choice in `localStorage` under `mbb-theme` (see [UI](systems/ui.md)).
