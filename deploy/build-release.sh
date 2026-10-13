#!/usr/bin/env bash
# Builds both services and the front end and packs everything the three VMs need:
#   release/shop-<version>.tar.gz
#     backend/shop-backend.jar  payment/payment-gateway.jar  frontend/dist/  nginx/  deploy/  scripts/
# Run on a build machine with JDK 21 and Node 22 (not on the VMs).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="${1:-$(date +%Y%m%d%H%M)}"
OUT="${ROOT}/release/shop-${VERSION}"

echo "==> Backend"
(cd "${ROOT}/backend" && ./mvnw -q -B clean package -DskipTests)
echo "==> Payment gateway"
(cd "${ROOT}/payment" && ./mvnw -q -B clean package -DskipTests)
echo "==> Front end"
(cd "${ROOT}/frontend" && npm ci --no-audit --no-fund && npm run build)

rm -rf "${OUT}"
mkdir -p "${OUT}/backend" "${OUT}/payment" "${OUT}/frontend"
cp "${ROOT}/backend/target/shop-backend.jar" "${OUT}/backend/"
cp "${ROOT}/payment/target/payment-gateway.jar" "${OUT}/payment/"
cp -r "${ROOT}/frontend/dist" "${OUT}/frontend/dist"
cp -r "${ROOT}/nginx" "${ROOT}/deploy" "${ROOT}/scripts" "${OUT}/"
echo "${VERSION}" > "${OUT}/VERSION"

tar -C "${ROOT}/release" -czf "${ROOT}/release/shop-${VERSION}.tar.gz" "shop-${VERSION}"
echo "==> release/shop-${VERSION}.tar.gz"
