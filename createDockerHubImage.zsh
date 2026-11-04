#!/bin/zsh

if [ -z "$1" ]; then
  echo "Error: Not enough arguments"
  echo "Usage: createDockerHubImage.zsh <version>"
  exit 1;
fi

# Unquoted on purpose: zsh word-splits command substitution, turning the output
# into separate --build-arg flags (values never contain spaces).
docker buildx build --platform linux/arm64,linux/amd64 --provenance=true --sbom=true \
$(./scripts/build-metadata.sh --docker-args) \
--push --tag ajpahl1008/mockingbird-bank:${1} .

echo "DockerHub Build Complete: ajpahl/mockingbird-bank:${1} "
