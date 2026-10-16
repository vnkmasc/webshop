#!/usr/bin/env bash
# Deploys the whole shop to the current kubectl context.
#   k8s/deploy.sh REGISTRY/PROJECT TAG [NAMESPACE]      (namespace default: webshop)
#   e.g. k8s/deploy.sh registry.example.com/shop 1a2b3c4
# The images REGISTRY/PROJECT/shop-{backend,payment,risk,frontend}:TAG must exist (scripts/build-images.sh -p)
# and be pullable by the nodes. On first run the Secret "shop-secrets" is created with random values;
# later runs keep it. Re-running with a new TAG rolls the services to the new images.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REGISTRY="${1:?usage: $0 REGISTRY/PROJECT TAG [NAMESPACE]}"
TAG="${2:?usage: $0 REGISTRY/PROJECT TAG [NAMESPACE]}"
NS="${3:-webshop}"
k() { kubectl -n "$NS" "$@"; }

kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -

if ! k get secret shop-secrets >/dev/null 2>&1; then
  rnd() { openssl rand -hex 24; }
  k create secret generic shop-secrets \
    --from-literal=DB_PASSWORD="$(rnd)" \
    --from-literal=PAYMENT_DB_PASSWORD="$(rnd)" \
    --from-literal=PAYMENT_API_KEY="$(rnd)" \
    --from-literal=RISK_API_KEY="$(rnd)" \
    --from-literal=JWT_SECRET="$(openssl rand -base64 48)" \
    --from-literal=APP_ADMIN_PASSWORD="$(openssl rand -base64 12 | tr -d '/+=')"
  echo "created secret shop-secrets"
fi

k apply -f "$HERE/config.yaml" -f "$HERE/postgres.yaml" -f "$HERE/kafka.yaml"
k rollout status statefulset/postgres --timeout=300s
# Placeholder image names in the manifests (shop-<component>:latest) point to the registry and tag.
for f in payment risk backend frontend; do
  sed -E "s#image: shop-([a-z]+):latest#image: ${REGISTRY}/shop-\\1:${TAG}#" "$HERE/$f.yaml" | k apply -f -
done
for d in payment risk backend frontend; do
  k rollout status "deploy/$d" --timeout=600s
done
k get pods -o wide
echo
echo "shop: http://<node IP>:$(k get svc frontend -o jsonpath='{.spec.ports[0].nodePort}')"
echo "admin: $(k get cm shop-config -o jsonpath='{.data.APP_ADMIN_EMAIL}'), password:"
echo "  kubectl -n $NS get secret shop-secrets -o jsonpath='{.data.APP_ADMIN_PASSWORD}' | base64 -d; echo"
