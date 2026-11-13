# JDK 21 + Gradle wrapper support, matching what CI and local dev both use (see
# AGENTS.md and Dockerfile). The docker-outside-of-docker feature (added via
# devcontainer.json, not here) gives this container a CLI that talks to the
# *host's* Docker daemon, which is what Testcontainers-backed integration tests
# (ApplicationSmokeTest, AccountTransactionQueryPerformanceTest,
# ProgramMigrationTest) need to start their own throwaway Postgres containers as
# siblings.
FROM mcr.microsoft.com/devcontainers/java:1-21-bookworm

# This base image ships an apt source for Yarn signed with a key that Yarn
# rotated out (NO_PUBKEY 62D54FD4003F6525), which breaks `apt-get update` for
# every later devcontainer feature layer, not just this one. This project
# uses npm (package-lock.json), not yarn, so drop the source outright rather
# than chase a working key for a package manager nothing here needs.
RUN rm -f /etc/apt/sources.list.d/yarn.list
