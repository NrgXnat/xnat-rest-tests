#!/usr/bin/env bash
# Builds an XNAT image for the two-node test environment from an XNAT checkout, using that
# checkout's own Dockerfile. FOR TESTING ONLY.
#
#   ./build-image.sh <xnat checkout> [image tag]
#
# The default tag is xnat:two-node-test. The checkout isn't modified: the WAR is built in
# place, and the image is built from a temporary copy of the Dockerfile and docker/.
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 ]]; then
    echo "Usage: $0 <xnat checkout> [image tag]" >&2
    exit 2
fi

xnat=$(cd "$1" && pwd)
tag=${2:-xnat:two-node-test}

(cd "$xnat" && ./gradlew :xnat-web:war -x test)

wars=()
for war in "$xnat"/xnat-web/build/libs/xnat-web-*.war; do
    [[ $war == *-plain.war ]] || wars+=("$war")
done
if [[ ${#wars[@]} -ne 1 ]]; then
    echo "Expected one WAR in $xnat/xnat-web/build/libs, found ${#wars[@]}" >&2
    exit 1
fi

context=$(mktemp -d)
trap 'rm -rf "$context"' EXIT
cp "$xnat/Dockerfile" "$context/"
cp -R "$xnat/docker" "$context/docker"
mkdir "$context/docker-context"
cp "${wars[0]}" "$context/docker-context/xnat.war"

docker build \
    --label "org.opencontainers.image.revision=$(git -C "$xnat" rev-parse HEAD)" \
    -t "$tag" "$context"

echo "Built $tag from $(git -C "$xnat" rev-parse --short HEAD). Use it with: export XNAT_IMAGE=$tag"
