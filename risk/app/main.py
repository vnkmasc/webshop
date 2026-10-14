"""Order risk scoring service for the shop backend.

POST /api/risk/score          score one order
GET  /healthz                 liveness/readiness
GET|PUT /api/admin/simulation/{name}   fault injection (RISK_SLOW, RISK_ERROR, RISK_TIMEOUT), set by the backend
Endpoints are plain (sync) functions on purpose: they run in the thread pool, which keeps tracing simple.
"""
import logging
import os
import random
import secrets

from fastapi import Depends, FastAPI, HTTPException, Request
from pydantic import BaseModel, Field

from . import scoring, simulation

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [rid=%(request_id)s] %(name)s: %(message)s")
log = logging.getLogger("risk")

API_KEY = os.environ.get("RISK_API_KEY", "")
app = FastAPI(title="risk", version=os.environ.get("APP_RELEASE_VERSION", "dev"))


class _RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "request_id"):
            record.request_id = "-"
        return True


for handler in logging.getLogger().handlers:
    handler.addFilter(_RequestIdFilter())


def require_key(request: Request) -> None:
    if API_KEY:
        given = request.headers.get("authorization", "").removeprefix("Bearer ")
        if not secrets.compare_digest(given, API_KEY):
            raise HTTPException(status_code=401, detail="Invalid API key")


class ScoreRequest(BaseModel):
    orderRef: str
    amount: int = Field(ge=0)
    paymentMethod: str
    phone: str
    province: str
    avgOrderValue: int = 0
    phoneOrders24h: int = 0
    previousProvinces: list[str] = []


class ScoreResponse(BaseModel):
    score: int
    level: str
    reasons: list[str]


class ScenarioUpdate(BaseModel):
    active: bool
    params: dict = {}
    until: str | None = None


@app.get("/healthz")
def healthz() -> dict:
    return {"status": "UP", "version": app.version}


@app.post("/api/risk/score", response_model=ScoreResponse, dependencies=[Depends(require_key)])
def score(req: ScoreRequest, request: Request) -> ScoreResponse:
    rid = {"request_id": request.headers.get("x-request-id", "-")}
    slow = simulation.param("RISK_SLOW", "delayMs", 2000)
    if slow is not None:
        simulation.sleep_ms(slow)
    timeout = simulation.param("RISK_TIMEOUT", "delayMs", 5000)
    if timeout is not None:
        simulation.sleep_ms(timeout)
    error = simulation.param("RISK_ERROR", "percent", 30)
    if error is not None and random.randrange(100) < error:
        raise RuntimeError(f"Simulated failure while scoring order {req.orderRef}")
    result = scoring.score(scoring.Order(
        order_ref=req.orderRef, amount=req.amount, payment_method=req.paymentMethod, phone=req.phone,
        province=req.province, avg_order_value=req.avgOrderValue, phone_orders_24h=req.phoneOrders24h,
        previous_provinces=req.previousProvinces))
    log.info("Scored order %s amount=%d method=%s -> %d %s %s", req.orderRef, req.amount, req.paymentMethod,
             result.score, result.level, ",".join(result.reasons) or "-", extra=rid)
    return ScoreResponse(score=result.score, level=result.level, reasons=result.reasons)


@app.get("/api/admin/simulation", dependencies=[Depends(require_key)])
def simulation_state() -> dict:
    return simulation.state()


@app.put("/api/admin/simulation/{name}", dependencies=[Depends(require_key)])
def simulation_update(name: str, body: ScenarioUpdate) -> dict:
    try:
        result = simulation.set_scenario(name, body.active, body.params, body.until)
    except KeyError:
        raise HTTPException(status_code=404, detail=f"Unknown scenario {name}")
    log.warning("Scenario %s %s params=%s until=%s", name, "on" if body.active else "off", body.params, body.until,
                extra={"request_id": "-"})
    return result
