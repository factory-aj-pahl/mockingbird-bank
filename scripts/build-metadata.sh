#!/usr/bin/env bash
# Prints the build metadata the Dockerfile's build args expect, computed from
# this git checkout - the Docker build context has no .git (see
# .dockerignore), so this is how the version and commit reach the image.
# Mirrors the version logic at the top of build.gradle, reading the same
# baseVersion from gradle.properties.
#
#   scripts/build-metadata.sh                KEY=value lines (e.g. >> "$GITHUB_OUTPUT")
#   scripts/build-metadata.sh --docker-args  --build-arg KEY=value ... for `docker build`
#
# Set APP_VERSION in the environment to override the computed
# <baseVersion>-<short sha>[-dirty] version (release.yml does, from the tag).
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

base_version="$(sed -n 's/^baseVersion=//p' gradle.properties)"

commit="$(git rev-parse HEAD 2>/dev/null || true)"
commit="${commit:-unknown}"
short_commit="${commit:0:7}"

branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || true)"
if [[ "$branch" == "HEAD" ]]; then
    branch="detached"
fi
branch="${branch:-unknown}"

if status="$(git status --porcelain 2>/dev/null)"; then
    if [[ -n "$status" ]]; then dirty="true"; else dirty="false"; fi
else
    dirty="unknown"
fi

commit_time="$(git show -s --format=%ct HEAD 2>/dev/null || true)"

version="${APP_VERSION:-}"
if [[ -z "$version" ]]; then
    version="${base_version}-${short_commit}"
    if [[ "$dirty" == "true" ]]; then
        version+="-dirty"
    fi
fi

pairs=(
    "APP_VERSION=${version}"
    "GIT_COMMIT=${commit}"
    "GIT_BRANCH=${branch}"
    "GIT_DIRTY=${dirty}"
    "GIT_COMMIT_TIME=${commit_time}"
)

if [[ "${1:-}" == "--docker-args" ]]; then
    for pair in "${pairs[@]}"; do
        printf -- '--build-arg %s ' "$pair"
    done
    echo
else
    printf '%s\n' "${pairs[@]}"
fi
