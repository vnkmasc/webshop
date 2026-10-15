package vn.saibuoc.shop.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Message on the order topic, keyed by order code. */
public record OrderEvent(String type, String code, BigDecimal total, String paymentMethod, String province,
                         List<Line> items, Instant placedAt) {

    public static final String ORDER_PLACED = "ORDER_PLACED";

    public record Line(long variantId, int quantity) {
    }
}
