#!/usr/bin/env bash
# Deploys the load generator to the current kubectl context.
#   ADMIN_PASSWORD=... ./deploy.sh BASE_URL IMAGE
#   e.g. ./deploy.sh http://shop.example.com registry.example.com/shop/shop-loadgen:1a2b3c4
# The image must be pullable by the nodes (see ../README.md for building and pushing it).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
BASE_URL="${1:?usage: $0 BASE_URL IMAGE}"
IMAGE="${2:?usage: $0 BASE_URL IMAGE}"
: "${ADMIN_PASSWORD:?set ADMIN_PASSWORD (APP_ADMIN_PASSWORD of the backend)}"
NS=shop-loadgen

kubectl apply -f "$HERE/shop-loadgen.yaml"
kubectl -n "$NS" get configmap shop-loadgen >/dev/null 2>&1 || kubectl apply -f "$HERE/config.yaml"
kubectl -n "$NS" patch configmap shop-loadgen --type merge -p "{\"data\":{\"BASE_URL\":\"$BASE_URL\"}}"
kubectl -n "$NS" create secret generic shop-loadgen --from-literal=ADMIN_PASSWORD="$ADMIN_PASSWORD" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl -n "$NS" set image deploy/shop-loadgen k6="$IMAGE"
kubectl -n "$NS" rollout restart deploy/shop-loadgen
kubectl -n "$NS" rollout status deploy/shop-loadgen --timeout=180s
kubectl -n "$NS" get pods -o wide
