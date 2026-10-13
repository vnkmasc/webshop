package vn.saibuoc.shop.admin;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.saibuoc.shop.order.OrderService;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    /** Orders that count as sales (placed and not cancelled or failed). */
    private static final String SOLD = "o.status NOT IN ('CANCELLED', 'PAYMENT_FAILED')";

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record Kpis(BigDecimal revenue, long orders, BigDecimal avgOrderValue, long itemsSold, long newCustomers,
                       long failedPayments, long cardAttempts, BigDecimal revenuePrevious, long ordersPrevious) {
    }

    public record DayPoint(LocalDate day, BigDecimal revenue, long orders) {
    }

    public record StatusCount(String status, long count) {
    }

    public record TopProduct(long productId, String name, String image, long quantity, BigDecimal revenue) {
    }

    public record CategoryRevenue(String category, BigDecimal revenue) {
    }

    public record LowStock(long productId, String productName, String size, int stock) {
    }

    public record RecentOrder(String code, String customerName, BigDecimal total, String status, String paymentMethod,
                              OffsetDateTime createdAt) {
    }

    public record Dashboard(int days, Kpis kpis, List<DayPoint> series, List<StatusCount> statuses,
                            List<TopProduct> topProducts, List<CategoryRevenue> categories, List<LowStock> lowStock,
                            List<RecentOrder> recent) {
    }

    public Dashboard dashboard(int days) {
        int d = Math.min(Math.max(days, 1), 365);
        LocalDate today = LocalDate.now(clock.withZone(OrderService.SHOP_ZONE));
        LocalDate fromDay = today.minusDays(d - 1L);
        OffsetDateTime from = fromDay.atStartOfDay(OrderService.SHOP_ZONE).toOffsetDateTime();
        OffsetDateTime to = today.plusDays(1).atStartOfDay(OrderService.SHOP_ZONE).toOffsetDateTime();
        OffsetDateTime prevFrom = from.minusDays(d);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("from", from).addValue("to", to).addValue("prevFrom", prevFrom)
                .addValue("fromDay", fromDay).addValue("toDay", today);

        Kpis kpis = jdbc.queryForObject("""
                SELECT
                  COALESCE(sum(o.total) FILTER (WHERE %1$s AND o.created_at >= :from), 0) AS revenue,
                  count(*) FILTER (WHERE %1$s AND o.created_at >= :from) AS orders,
                  COALESCE(sum(o.total) FILTER (WHERE %1$s AND o.created_at < :from), 0) AS revenue_prev,
                  count(*) FILTER (WHERE %1$s AND o.created_at < :from) AS orders_prev,
                  count(*) FILTER (WHERE o.status = 'PAYMENT_FAILED' AND o.created_at >= :from) AS failed,
                  count(*) FILTER (WHERE o.payment_method = 'CARD' AND o.created_at >= :from) AS card_attempts
                FROM orders o WHERE o.created_at >= :prevFrom AND o.created_at < :to""".formatted(SOLD), p,
                (rs, i) -> {
                    BigDecimal revenue = rs.getBigDecimal("revenue");
                    long orders = rs.getLong("orders");
                    long items = itemsSold(p);
                    long customers = newCustomers(p);
                    return new Kpis(revenue, orders, orders == 0 ? BigDecimal.ZERO
                            : revenue.divide(BigDecimal.valueOf(orders), 0, java.math.RoundingMode.HALF_UP), items,
                            customers, rs.getLong("failed"), rs.getLong("card_attempts"),
                            rs.getBigDecimal("revenue_prev"), rs.getLong("orders_prev"));
                });

        List<DayPoint> series = jdbc.query("""
                SELECT g.day::date AS day, COALESCE(sum(o.total), 0) AS revenue, count(o.id) AS orders
                FROM generate_series(CAST(:fromDay AS date), CAST(:toDay AS date), interval '1 day') AS g(day)
                LEFT JOIN orders o ON (o.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date = g.day::date AND %s
                GROUP BY g.day ORDER BY g.day""".formatted(SOLD), p,
                (rs, i) -> new DayPoint(rs.getObject("day", LocalDate.class), rs.getBigDecimal("revenue"), rs.getLong("orders")));

        List<StatusCount> statuses = jdbc.query("""
                SELECT status, count(*) AS n FROM orders WHERE created_at >= :from GROUP BY status ORDER BY n DESC""", p,
                (rs, i) -> new StatusCount(rs.getString("status"), rs.getLong("n")));

        List<TopProduct> top = jdbc.query("""
                SELECT i.product_id, max(i.product_name) AS name, max(i.image) AS image,
                       sum(i.quantity) AS qty, sum(i.line_total) AS revenue
                FROM order_items i JOIN orders o ON o.id = i.order_id
                WHERE o.created_at >= :from AND %s
                GROUP BY i.product_id ORDER BY sum(i.quantity) DESC LIMIT 8""".formatted(SOLD), p,
                (rs, i) -> new TopProduct(rs.getLong("product_id"), rs.getString("name"), rs.getString("image"),
                        rs.getLong("qty"), rs.getBigDecimal("revenue")));

        List<CategoryRevenue> categories = jdbc.query("""
                SELECT c.name, sum(i.line_total) AS revenue
                FROM order_items i JOIN orders o ON o.id = i.order_id
                JOIN products p ON p.id = i.product_id JOIN categories c ON c.id = p.category_id
                WHERE o.created_at >= :from AND %s
                GROUP BY c.name ORDER BY sum(i.line_total) DESC""".formatted(SOLD), p,
                (rs, i) -> new CategoryRevenue(rs.getString("name"), rs.getBigDecimal("revenue")));

        List<LowStock> low = jdbc.query("""
                SELECT p.id, p.name, v.size, v.stock FROM product_variants v JOIN products p ON p.id = v.product_id
                WHERE p.active AND v.stock <= 3 ORDER BY v.stock, p.sold_count DESC LIMIT 12""", p,
                (rs, i) -> new LowStock(rs.getLong("id"), rs.getString("name"), rs.getString("size"), rs.getInt("stock")));

        List<RecentOrder> recent = jdbc.query("""
                SELECT code, customer_name, total, status, payment_method, created_at FROM orders
                ORDER BY created_at DESC LIMIT 10""", p,
                (rs, i) -> new RecentOrder(rs.getString("code"), rs.getString("customer_name"), rs.getBigDecimal("total"),
                        rs.getString("status"), rs.getString("payment_method"),
                        rs.getObject("created_at", OffsetDateTime.class)));

        return new Dashboard(d, kpis, series, statuses, top, categories, low, recent);
    }

    private long itemsSold(MapSqlParameterSource p) {
        Long n = jdbc.queryForObject("""
                SELECT COALESCE(sum(i.quantity), 0) FROM order_items i JOIN orders o ON o.id = i.order_id
                WHERE o.created_at >= :from AND o.created_at < :to AND %s""".formatted(SOLD), p, Long.class);
        return n == null ? 0 : n;
    }

    private long newCustomers(MapSqlParameterSource p) {
        Long n = jdbc.queryForObject("""
                SELECT count(*) FROM users WHERE role = 'CUSTOMER' AND created_at >= :from AND created_at < :to""",
                p, Long.class);
        return n == null ? 0 : n;
    }
}
