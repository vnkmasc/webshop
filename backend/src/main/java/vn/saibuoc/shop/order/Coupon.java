package vn.saibuoc.shop.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "coupons")
public class Coupon {

    public enum Type { PERCENT, FIXED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private BigDecimal value;

    @Column(name = "min_order", nullable = false)
    private BigDecimal minOrder = BigDecimal.ZERO;

    @Column(name = "max_discount")
    private BigDecimal maxDiscount;

    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count")
    private int usedCount;

    private boolean active = true;

    /** Why the coupon cannot be used for this order, or null when it can. */
    public String rejectReason(BigDecimal subtotal, LocalDate today) {
        if (!active || today.isBefore(startsOn) || today.isAfter(endsOn)) {
            return "Mã giảm giá đã hết hạn hoặc chưa đến thời gian áp dụng";
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            return "Mã giảm giá đã hết lượt sử dụng";
        }
        if (subtotal.compareTo(minOrder) < 0) {
            return "Đơn hàng tối thiểu " + String.format("%,d", minOrder.longValue()).replace(',', '.') + "đ để dùng mã này";
        }
        return null;
    }

    public BigDecimal discountFor(BigDecimal subtotal) {
        BigDecimal d = type == Type.PERCENT
                ? subtotal.multiply(value).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                : value;
        if (maxDiscount != null && d.compareTo(maxDiscount) > 0) {
            d = maxDiscount;
        }
        return d.min(subtotal);
    }
}
