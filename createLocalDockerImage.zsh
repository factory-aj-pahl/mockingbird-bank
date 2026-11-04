#!/bin/zsh

if [ -z "$1" ]; then
  echo "Error: Not enough arguments"
  echo "Usage: createLocalDockerImage.zsh <version>"
  exit 1;
fi

# Unquoted on purpose: zsh word-splits command substitution, turning the output
# into separate --build-arg flags (values never contain spaces).
docker buildx build --platform linux/arm64 --load $(./scripts/build-metadata.sh --docker-args) \
--tag ajpahl1008/mockingbird-bank:${1} .

echo "Build Complete: ajpahl1008/mockingbird-bank:${1} "
