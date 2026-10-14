"""Fault injection driven by the shop backend: scenarios with an expiry, kept in memory."""
import threading
import time
from datetime import datetime, timezone

SCENARIOS = {"RISK_SLOW", "RISK_ERROR", "RISK_TIMEOUT"}

_lock = threading.Lock()
_active: dict[str, dict] = {}


def set_scenario(name: str, active: bool, params: dict | None, until: str | None) -> dict:
    if name not in SCENARIOS:
        raise KeyError(name)
    with _lock:
        if active and until:
            _active[name] = {"params": params or {}, "until": datetime.fromisoformat(until.replace("Z", "+00:00"))}
        else:
            _active.pop(name, None)
    return state()


def state() -> dict:
    now = datetime.now(timezone.utc)
    with _lock:
        for name in [n for n, s in _active.items() if s["until"] <= now]:
            _active.pop(name)
        return {n: {"params": s["params"], "until": s["until"].isoformat()} for n, s in _active.items()}


def param(name: str, key: str, default: int) -> int | None:
    """The parameter of an active scenario, or None when the scenario is off."""
    current = state().get(name)
    if current is None:
        return None
    try:
        return int(current["params"].get(key, default))
    except (TypeError, ValueError):
        return default


def sleep_ms(ms: int) -> None:
    time.sleep(max(ms, 0) / 1000)
