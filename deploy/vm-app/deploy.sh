#!/usr/bin/env bash
# Deploys a new jar for one service with automatic rollback when it does not become healthy.
# Usage: sudo ./deploy.sh backend path/to/shop-backend.jar
#        sudo ./deploy.sh payment path/to/payment-gateway.jar
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source-path=SCRIPTDIR source=../common/lib.sh
source "${HERE}/../common/lib.sh"
require_root

SERVICE="${1:?usage: deploy.sh backend|payment path/to/jar}"
JAR="${2:?usage: deploy.sh backend|payment path/to/jar}"
[[ -f "${JAR}" ]] || die "${JAR} not found"
case "${SERVICE}" in
    backend) UNIT=shop-backend; TARGET=/opt/shop/backend/shop-backend.jar
             HEALTH_URL=http://127.0.0.1:8081/actuator/health/readiness; INFO_URL=http://127.0.0.1:8081/actuator/info ;;
    payment) UNIT=shop-payment; TARGET=/opt/shop/payment/payment-gateway.jar
             HEALTH_URL=http://127.0.0.1:8090/actuator/health/readiness; INFO_URL=http://127.0.0.1:8090/actuator/info ;;
    *) die "unknown service ${SERVICE} (backend|payment)" ;;
esac
WAIT_SECONDS="${WAIT_SECONDS:-300}"

if [[ -f "${TARGET}" ]]; then
    cp -p "${TARGET}" "${TARGET}.prev"
fi
install -o shop -g shop -m 644 "${JAR}" "${TARGET}"

wait_healthy() {
    local waited=0
    while (( waited < WAIT_SECONDS )); do
        if curl -fs "${HEALTH_URL}" | grep -q '"status":"UP"'; then
            return 0
        fi
        sleep 5
        waited=$((waited + 5))
    done
    return 1
}

log "Restarting ${UNIT}"
systemctl restart "${UNIT}"
if wait_healthy; then
    log "${UNIT} is healthy: $(curl -fs "${INFO_URL}" || true)"
    exit 0
fi

warn "${UNIT} did not become healthy within ${WAIT_SECONDS}s; last log lines:"
journalctl -u "${UNIT}" -n 60 --no-pager || true
if [[ -f "${TARGET}.prev" ]]; then
    warn "Rolling back to the previous jar"
    install -o shop -g shop -m 644 "${TARGET}.prev" "${TARGET}"
    systemctl restart "${UNIT}"
    if wait_healthy; then
        warn "Rolled back successfully"
    else
        die "Rollback also failed - check the logs"
    fi
fi
die "Deployment failed"
