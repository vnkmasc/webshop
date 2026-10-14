"""Order risk scoring: pure functions, no I/O (unit-tested in tests/test_scoring.py)."""
from dataclasses import dataclass, field

HIGH = 60
MEDIUM = 30
COD_HIGH_VALUE_VND = 5_000_000


@dataclass
class Order:
    order_ref: str
    amount: int
    payment_method: str
    phone: str
    province: str
    avg_order_value: int = 0
    phone_orders_24h: int = 0
    previous_provinces: list[str] = field(default_factory=list)


@dataclass
class Score:
    score: int
    level: str
    reasons: list[str]


def score(order: Order) -> Score:
    points = 0
    reasons: list[str] = []
    if order.avg_order_value > 0:
        ratio = order.amount / order.avg_order_value
        if ratio >= 5:
            points += 40
            reasons.append("AMOUNT_UNUSUAL")
        elif ratio >= 3:
            points += 30
            reasons.append("AMOUNT_UNUSUAL")
    if order.phone_orders_24h >= 5:
        points += 35
        reasons.append("REPEAT_PHONE_24H")
    elif order.phone_orders_24h >= 3:
        points += 25
        reasons.append("REPEAT_PHONE_24H")
    if order.payment_method == "COD" and order.amount >= COD_HIGH_VALUE_VND:
        points += 30
        reasons.append("COD_HIGH_VALUE")
    if order.previous_provinces and order.province not in order.previous_provinces:
        points += 15
        reasons.append("NEW_PROVINCE")
    points = min(points, 100)
    level = "HIGH" if points >= HIGH else "MEDIUM" if points >= MEDIUM else "LOW"
    return Score(points, level, reasons)
