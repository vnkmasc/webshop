#!/usr/bin/env bash
# Builds the container images of the shop, tagged with the git commit and "latest":
#   shop-backend    backend/Containerfile    (builds the jar first: needs JDK 21)
#   shop-payment    payment/Containerfile    (builds the jar first: needs JDK 21)
#   shop-risk       risk/Containerfile
#   shop-frontend   frontend/Containerfile   (builds the static site first: needs Node 22)
# Usage:
#   scripts/build-images.sh [-p REGISTRY/PROJECT] [component ...]     # default: all four
#   scripts/build-images.sh -p registry.example.com/shop               # build all and push
#   scripts/build-images.sh -p registry.example.com/shop backend       # one image
# Log in to the registry first (podman login / docker login). Uses podman, or docker when podman is absent.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BUILDER="$(command -v podman || command -v docker || true)"
[[ -n "$BUILDER" ]] || { echo "podman or docker is required" >&2; exit 2; }
PUSH=""
if [[ "${1:-}" == "-p" ]]; then PUSH="${2:?-p needs REGISTRY/PROJECT}"; shift 2; fi
TAG="$(git -C "$ROOT" rev-parse --short HEAD)"
[[ -z "$(git -C "$ROOT" status --porcelain)" ]] || TAG="${TAG}-dirty"
COMPONENTS=("$@"); [[ ${#COMPONENTS[@]} -gt 0 ]] || COMPONENTS=(backend payment risk frontend)

for c in "${COMPONENTS[@]}"; do
  case "$c" in
    backend|payment)
      (cd "$ROOT/$c" && ./mvnw -q package -DskipTests)
      ctx="$ROOT/$c/target" ;;
    risk)
      ctx="$ROOT/risk" ;;
    frontend)
      (cd "$ROOT/frontend" && npm ci --no-audit --no-fund && npm run build)
      ctx="$ROOT/frontend" ;;
    *) echo "unknown component: $c (backend, payment, risk, frontend)" >&2; exit 2 ;;
  esac
  name="shop-$c"
  "$BUILDER" build -f "$ROOT/$c/Containerfile" -t "$name:$TAG" -t "$name:latest" "$ctx"
  echo "built $name:$TAG"
  if [[ -n "$PUSH" ]]; then
    for t in "$TAG" latest; do
      "$BUILDER" tag "$name:$t" "$PUSH/$name:$t"
      "$BUILDER" push "$PUSH/$name:$t"
    done
    echo "pushed $PUSH/$name:$TAG"
  fi
done
echo "tag: $TAG"
