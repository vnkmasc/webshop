import unittest

from app.scoring import Order, score


def order(**kw):
    base = dict(order_ref="SB1", amount=1_000_000, payment_method="CARD", phone="0911111111", province="Hà Nội",
                avg_order_value=1_000_000, phone_orders_24h=0, previous_provinces=[])
    base.update(kw)
    return Order(**base)


class ScoringTest(unittest.TestCase):
    def test_ordinary_order_is_low(self):
        s = score(order())
        self.assertEqual((s.score, s.level, s.reasons), (0, "LOW", []))

    def test_high_value_cod_from_repeat_phone_is_high(self):
        s = score(order(amount=6_000_000, payment_method="COD", phone_orders_24h=3))
        self.assertEqual(s.level, "HIGH")
        self.assertEqual(set(s.reasons), {"AMOUNT_UNUSUAL", "COD_HIGH_VALUE", "REPEAT_PHONE_24H"})

    def test_new_province_only_counts_with_history(self):
        self.assertNotIn("NEW_PROVINCE", score(order(previous_provinces=[])).reasons)
        s = score(order(province="Đà Nẵng", previous_provinces=["Hà Nội"]))
        self.assertEqual((s.score, s.level, s.reasons), (15, "LOW", ["NEW_PROVINCE"]))

    def test_score_is_capped(self):
        s = score(order(amount=50_000_000, payment_method="COD", phone_orders_24h=9, province="X", previous_provinces=["Y"]))
        self.assertEqual(s.score, 100)


if __name__ == "__main__":
    unittest.main()
