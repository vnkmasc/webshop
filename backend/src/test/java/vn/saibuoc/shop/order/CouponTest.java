package vn.saibuoc.shop.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CouponTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 13);

    private static Coupon coupon(Coupon.Type type, long value, long minOrder, Long max) {
        Coupon c = new Coupon();
        c.setCode("TEST");
        c.setType(type);
        c.setValue(BigDecimal.valueOf(value));
        c.setMinOrder(BigDecimal.valueOf(minOrder));
        c.setMaxDiscount(max == null ? null : BigDecimal.valueOf(max));
        c.setStartsOn(TODAY.minusDays(1));
        c.setEndsOn(TODAY.plusDays(1));
        return c;
    }

    @Test
    void percentDiscountIsCapped() {
        Coupon c = coupon(Coupon.Type.PERCENT, 10, 0, 100_000L);
        assertThat(c.discountFor(BigDecimal.valueOf(500_000))).isEqualByComparingTo("50000");
        assertThat(c.discountFor(BigDecimal.valueOf(3_000_000))).isEqualByComparingTo("100000");
    }

    @Test
    void fixedDiscountNeverExceedsSubtotal() {
        Coupon c = coupon(Coupon.Type.FIXED, 200_000, 0, null);
        assertThat(c.discountFor(BigDecimal.valueOf(150_000))).isEqualByComparingTo("150000");
    }

    @Test
    void rejectsBelowMinimumExpiredAndUsedUp() {
        Coupon c = coupon(Coupon.Type.FIXED, 200_000, 2_000_000, null);
        assertThat(c.rejectReason(BigDecimal.valueOf(1_999_000), TODAY)).contains("tối thiểu 2.000.000đ");
        assertThat(c.rejectReason(BigDecimal.valueOf(2_000_000), TODAY)).isNull();
        assertThat(c.rejectReason(BigDecimal.valueOf(2_000_000), TODAY.plusDays(2))).contains("hết hạn");
        c.setUsageLimit(5);
        c.setUsedCount(5);
        assertThat(c.rejectReason(BigDecimal.valueOf(2_000_000), TODAY)).contains("hết lượt");
    }

    @Test
    void orderTransitionsFollowTheLifeCycle() {
        assertThat(OrderService.nextStatuses(Order.Status.PENDING))
                .containsExactlyInAnyOrder(Order.Status.CONFIRMED, Order.Status.CANCELLED);
        assertThat(OrderService.nextStatuses(Order.Status.SHIPPING)).containsExactly(Order.Status.DELIVERED);
        assertThat(OrderService.nextStatuses(Order.Status.DELIVERED)).isEmpty();
        assertThat(OrderService.nextStatuses(Order.Status.PAYMENT_FAILED)).isEmpty();
    }
}
