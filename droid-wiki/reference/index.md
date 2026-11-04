# Reference

Lookup tables for Mockingbird Bank: every setting you can change, every table and column in the database, and every library, image and GitHub Action the build pulls in. The explanations of how these pieces behave live in the system and feature pages linked from each table.

| Page | What it lists |
| --- | --- |
| [Configuration](configuration.md) | Spring properties in `src/main/resources/application.yml`, app-specific env vars, Gradle `-P` properties, Docker build args, Compose and Kubernetes settings, and CI secrets |
| [Data models](data-models.md) | The four tables from `src/main/resources/db/migration/V1__init_schema.sql`, column by column, mapped to the JPA entities, plus the seed data |
| [Dependencies](dependencies.md) | Gradle plugins and libraries with versions, the `qa/` npm package, Docker base images, GitHub Actions, and the Dependabot setup |

For behavior rather than lookup, start at [Architecture](../overview/architecture.md), [Persistence](../systems/persistence.md) or [Feature flags](../features/feature-flags.md).
