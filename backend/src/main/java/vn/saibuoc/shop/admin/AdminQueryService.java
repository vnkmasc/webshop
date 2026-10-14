package vn.saibuoc.shop.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.common.PageResponse;
import vn.saibuoc.shop.order.Coupon;
import vn.saibuoc.shop.order.CouponRepository;
import vn.saibuoc.shop.order.OrderService;

/** Admin lists (orders, customers) and coupon management. */
@Service
@RequiredArgsConstructor
public class AdminQueryService {

    private final NamedParameterJdbcTemplate jdbc;
    private final CouponRepository coupons;

    public record OrderRow(String code, String customerName, String phone, String province, BigDecimal total,
                           String status, String paymentMethod, String paymentStatus, int itemCount, String couponCode,
                           OffsetDateTime createdAt, String riskLevel, Integer riskScore) {
    }

    public record StatusCount(String status, long count) {
    }

    public record CustomerRow(long id, String email, String fullName, String phone, String province, boolean active,
                              OffsetDateTime createdAt, OffsetDateTime lastLoginAt, long orders, BigDecimal spent) {
    }

    public record CouponDto(long id, String code, String description, String type, BigDecimal value,
                            BigDecimal minOrder, BigDecimal maxDiscount, LocalDate startsOn, LocalDate endsOn,
                            Integer usageLimit, int usedCount, boolean active) {
    }

    public record CouponRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "Mã gồm 3-30 ký tự chữ, số, - hoặc _") String code,
            @Size(max = 160) String description,
            @NotNull Coupon.Type type,
            @NotNull @Positive BigDecimal value,
            @NotNull @Min(0) BigDecimal minOrder,
            @Positive BigDecimal maxDiscount,
            @NotNull LocalDate startsOn,
            @NotNull LocalDate endsOn,
            @Positive Integer usageLimit,
            boolean active) {
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderRow> orders(String status, String q, LocalDate from, LocalDate to, String payment,
                                         String risk, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("TRUE");
        if (status != null && !status.isBlank()) {
            where.append(" AND o.status = :status");
            p.addValue("status", status);
        }
        if (risk != null && !risk.isBlank()) {
            where.append(" AND o.risk_level = :risk");
            p.addValue("risk", risk);
        }
        if (payment != null && !payment.isBlank()) {
            where.append(" AND o.payment_method = :payment");
            p.addValue("payment", payment);
        }
        if (q != null && !q.isBlank()) {
            where.append(" AND (o.code ILIKE :q OR o.phone LIKE :q OR o.customer_name ILIKE :q OR o.email ILIKE :q)");
            p.addValue("q", "%" + q.trim() + "%");
        }
        if (from != null) {
            where.append(" AND o.created_at >= :from");
            p.addValue("from", from.atStartOfDay(OrderService.SHOP_ZONE).toOffsetDateTime());
        }
        if (to != null) {
            where.append(" AND o.created_at < :to");
            p.addValue("to", to.plusDays(1).atStartOfDay(OrderService.SHOP_ZONE).toOffsetDateTime());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM orders o WHERE " + where, p, Long.class);
        p.addValue("limit", pageSize).addValue("offset", (long) Math.max(page, 0) * pageSize);
        List<OrderRow> rows = jdbc.query("""
                SELECT o.code, o.customer_name, o.phone, o.province, o.total, o.status, o.payment_method,
                       o.payment_status, o.coupon_code, o.created_at, o.risk_level, o.risk_score,
                       (SELECT COALESCE(sum(i.quantity), 0) FROM order_items i WHERE i.order_id = o.id) AS item_count
                FROM orders o WHERE %s ORDER BY o.created_at DESC LIMIT :limit OFFSET :offset""".formatted(where), p,
                (rs, i) -> new OrderRow(rs.getString("code"), rs.getString("customer_name"), rs.getString("phone"),
                        rs.getString("province"), rs.getBigDecimal("total"), rs.getString("status"),
                        rs.getString("payment_method"), rs.getString("payment_status"), rs.getInt("item_count"),
                        rs.getString("coupon_code"), rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("risk_level"), (Integer) rs.getObject("risk_score")));
        return PageResponse.of(rows, Math.max(page, 0), pageSize, total);
    }

    @Transactional(readOnly = true)
    public List<StatusCount> orderStatusCounts() {
        return jdbc.query("SELECT status, count(*) AS n FROM orders GROUP BY status", new MapSqlParameterSource(),
                (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("n")));
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerRow> customers(String q, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("u.role = 'CUSTOMER'");
        if (q != null && !q.isBlank()) {
            where.append(" AND (u.email ILIKE :q OR u.full_name ILIKE :q OR u.phone LIKE :q)");
            p.addValue("q", "%" + q.trim() + "%");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM users u WHERE " + where, p, Long.class);
        p.addValue("limit", pageSize).addValue("offset", (long) Math.max(page, 0) * pageSize);
        List<CustomerRow> rows = jdbc.query("""
                SELECT u.id, u.email, u.full_name, u.phone, u.province, u.active, u.created_at, u.last_login_at,
                       count(o.id) AS orders,
                       COALESCE(sum(o.total) FILTER (WHERE o.status NOT IN ('CANCELLED', 'PAYMENT_FAILED')), 0) AS spent
                FROM users u LEFT JOIN orders o ON o.user_id = u.id
                WHERE %s GROUP BY u.id ORDER BY u.created_at DESC LIMIT :limit OFFSET :offset""".formatted(where), p,
                (rs, i) -> new CustomerRow(rs.getLong("id"), rs.getString("email"), rs.getString("full_name"),
                        rs.getString("phone"), rs.getString("province"), rs.getBoolean("active"),
                        rs.getObject("created_at", OffsetDateTime.class), rs.getObject("last_login_at", OffsetDateTime.class),
                        rs.getLong("orders"), rs.getBigDecimal("spent")));
        return PageResponse.of(rows, Math.max(page, 0), pageSize, total);
    }

    @Transactional(readOnly = true)
    public List<CouponDto> coupons() {
        return coupons.findAll(org.springframework.data.domain.Sort.by("id").descending()).stream()
                .map(AdminQueryService::toDto).toList();
    }

    @Transactional
    public CouponDto createCoupon(CouponRequest req) {
        validate(req);
        if (coupons.findByCode(req.code()).isPresent()) {
            throw BusinessException.conflict("COUPON_EXISTS", "Mã giảm giá đã tồn tại");
        }
        Coupon c = new Coupon();
        apply(c, req);
        return toDto(coupons.save(c));
    }

    @Transactional
    public CouponDto updateCoupon(long id, CouponRequest req) {
        validate(req);
        Coupon c = coupons.findById(id).orElseThrow(() -> BusinessException.notFound("mã giảm giá"));
        coupons.findByCode(req.code()).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw BusinessException.conflict("COUPON_EXISTS", "Mã giảm giá đã tồn tại");
        });
        apply(c, req);
        return toDto(c);
    }

    private static void validate(CouponRequest req) {
        if (req.endsOn().isBefore(req.startsOn())) {
            throw BusinessException.badRequest("DATE_RANGE", "Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (req.type() == Coupon.Type.PERCENT && req.value().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw BusinessException.badRequest("PERCENT_RANGE", "Phần trăm giảm tối đa 100");
        }
    }

    private static void apply(Coupon c, CouponRequest req) {
        c.setCode(req.code().trim().toUpperCase());
        c.setDescription(req.description() == null ? "" : req.description().trim());
        c.setType(req.type());
        c.setValue(req.value());
        c.setMinOrder(req.minOrder());
        c.setMaxDiscount(req.maxDiscount());
        c.setStartsOn(req.startsOn());
        c.setEndsOn(req.endsOn());
        c.setUsageLimit(req.usageLimit());
        c.setActive(req.active());
    }

    private static CouponDto toDto(Coupon c) {
        return new CouponDto(c.getId(), c.getCode(), c.getDescription(), c.getType().name(), c.getValue(),
                c.getMinOrder(), c.getMaxDiscount(), c.getStartsOn(), c.getEndsOn(), c.getUsageLimit(),
                c.getUsedCount(), c.isActive());
    }
}
