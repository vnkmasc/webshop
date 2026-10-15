#!/usr/bin/env bash
# Builds the container images of this repo, tagged with the git commit and "latest":
#   shop-payment   payment/Containerfile (builds the jar first, needs JDK 21)
#   shop-risk      risk/Containerfile
#   shop-loadgen   loadgen/Containerfile
# Usage:
#   scripts/build-images.sh [-p REGISTRY/PROJECT] [image ...]    # default: all three
#   scripts/build-images.sh -p registry.example.com/shop loadgen   # build loadgen and push it
# Log in to the registry first (docker login / podman login). Uses podman, or docker when podman is absent.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILDER="$(command -v podman || command -v docker || true)"
[[ -n "$BUILDER" ]] || { echo "podman or docker is required" >&2; exit 2; }
PUSH=""
if [[ "${1:-}" == "-p" ]]; then PUSH="${2:?-p needs REGISTRY/PROJECT}"; shift 2; fi
TAG="$(git -C "$ROOT" rev-parse --short HEAD)"
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || TAG="${TAG}-dirty"
IMAGES=("$@"); [[ ${#IMAGES[@]} -gt 0 ]] || IMAGES=(payment risk loadgen)

for img in "${IMAGES[@]}"; do
  case "$img" in
    payment) (cd "$ROOT/payment" && ./mvnw -q package -DskipTests)
             ctx="$ROOT/payment/target"; file="$ROOT/payment/Containerfile" ;;
    risk)    ctx="$ROOT/risk";    file="$ROOT/risk/Containerfile" ;;
    loadgen) ctx="$ROOT/loadgen"; file="$ROOT/loadgen/Containerfile" ;;
    *) echo "unknown image: $img (payment, risk, loadgen)" >&2; exit 2 ;;
  esac
  name="shop-$img"
  "$BUILDER" build -f "$file" -t "$name:$TAG" -t "$name:latest" "$ctx"
  echo "built $name:$TAG"
  if [[ -n "$PUSH" ]]; then
    for t in "$TAG" latest; do
      "$BUILDER" tag "$name:$t" "$PUSH/$name:$t"
      "$BUILDER" push "$PUSH/$name:$t"
    done
    echo "pushed $PUSH/$name:$TAG"
  fi
done
