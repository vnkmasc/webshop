package vn.saibuoc.shop.seed;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.saibuoc.shop.common.Slugs;
import vn.saibuoc.shop.config.AppProperties;
import vn.saibuoc.shop.order.OrderService;

/**
 * Creates the admin account and, when APP_SEED_ENABLED=true and the catalogue is empty, a full sample shop:
 * categories, ~100 shoes with sizes and stock, customers, coupons and 90 days of order history.
 * Uses plain JDBC in a single transaction, so it either completes or leaves nothing behind.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder {

    public static final String DEMO_PASSWORD = "SaiBuoc@2026";
    private static final int HISTORY_DAYS = 90;

    private final AppProperties props;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final Random random = new Random(20261013L);

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        ensureAdmin();
        if (!props.seedEnabled()) {
            return;
        }
        Long categories = jdbc.queryForObject("select count(*) from categories", Long.class);
        if (categories != null && categories > 0) {
            log.info("Sample data already present, seeding skipped");
            return;
        }
        log.warn("Seeding sample shop data (APP_SEED_ENABLED=true) - do not enable with real data");
        long start = System.currentTimeMillis();
        tx.executeWithoutResult(s -> seed());
        log.info("Sample data seeded in {} ms", System.currentTimeMillis() - start);
    }

    /** The admin account is created on first start from APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD (or the demo password). */
    private void ensureAdmin() {
        String email = props.adminEmail();
        Long exists = jdbc.queryForObject("select count(*) from users where lower(email) = lower(?)", Long.class, email);
        if (exists != null && exists > 0) {
            return;
        }
        String password = props.adminPassword();
        if (password == null || password.isBlank()) {
            if (!props.seedEnabled()) {
                log.warn("No admin account and APP_ADMIN_PASSWORD is empty: admin pages are unusable until it is set");
                return;
            }
            password = DEMO_PASSWORD;
        }
        jdbc.update("insert into users (email, password_hash, full_name, role) values (?, ?, ?, 'ADMIN')",
                email.toLowerCase(), passwordEncoder.encode(password), "Quản trị cửa hàng");
        log.info("Admin account {} created", email);
    }

    // ------------------------------------------------------------------ catalogue data

    private record CategorySpec(String slug, String name, String description, String style, String[] genders,
                                int minPrice, int maxPrice, String[] models, String blurb) {
    }

    private record ColorSpec(String key, String name) {
    }

    private static final String[] BRANDS = {"Sải Bước", "Velo", "Northpeak", "Bamboo Run", "Mekong", "Urbanist", "Lotus", "Kova"};

    private static final ColorSpec[] COLORS = {
            new ColorSpec("den", "Đen"), new ColorSpec("trang", "Trắng"), new ColorSpec("xam", "Xám"),
            new ColorSpec("navy", "Xanh navy"), new ColorSpec("do", "Đỏ"), new ColorSpec("nau", "Nâu"),
            new ColorSpec("be", "Be"), new ColorSpec("xanhla", "Xanh lá"), new ColorSpec("hong", "Hồng"),
            new ColorSpec("vang", "Vàng"), new ColorSpec("cam", "Cam"), new ColorSpec("xanhduong", "Xanh dương")};

    private static final List<CategorySpec> CATEGORIES = List.of(
            new CategorySpec("giay-chay-bo", "Giày chạy bộ", "Đệm êm, nhẹ, thoáng khí cho từng cây số", "running",
                    new String[]{"MEN", "WOMEN", "UNISEX"}, 1_190_000, 3_490_000,
                    new String[]{"Swift", "Breeze", "Pulse", "Tempo", "Glide", "Stride", "Aero", "Cloud"},
                    "Đế giữa đệm bọt phản hồi tốt, thân lưới dệt thoáng khí và đế ngoài cao su bám đường. Phù hợp chạy hằng ngày từ 5 đến 21 km."),
            new CategorySpec("sneaker", "Sneaker", "Phong cách đường phố cho mọi ngày", "sneaker",
                    new String[]{"MEN", "WOMEN", "UNISEX"}, 890_000, 2_790_000,
                    new String[]{"Classic", "Court", "Street", "Retro", "Low", "Canvas", "Club", "Daily"},
                    "Thân da tổng hợp dễ vệ sinh, đế cupsole chắc chắn, lót giày êm. Dễ phối với quần jeans, kaki hay váy."),
            new CategorySpec("giay-tay", "Giày tây công sở", "Lịch lãm cho văn phòng và sự kiện", "oxford",
                    new String[]{"MEN"}, 1_490_000, 3_990_000,
                    new String[]{"Oxford", "Derby", "Loafer", "Monk", "Brogue", "Executive"},
                    "Da bò thật, đóng đế thủ công, lót da thoáng khí. Form ôm chân, phù hợp đi làm và dự tiệc."),
            new CategorySpec("boot", "Boot & Chelsea", "Cá tính, bền bỉ mọi thời tiết", "boot",
                    new String[]{"MEN", "WOMEN", "UNISEX"}, 1_790_000, 4_490_000,
                    new String[]{"Chelsea", "Ranger", "Trail", "Desert", "Combat", "Hiker"},
                    "Da lộn chống thấm nhẹ, đế cao su chống trơn trượt, cổ chun dễ xỏ. Giữ ấm tốt khi trời lạnh."),
            new CategorySpec("sandal", "Sandal & dép", "Thoải mái cho ngày hè", "sandal",
                    new String[]{"MEN", "WOMEN", "UNISEX"}, 349_000, 890_000,
                    new String[]{"Summer", "Beach", "Strap", "Slide", "Trek", "Easy"},
                    "Quai dù chắc chắn, khóa dán điều chỉnh, đế EVA nhẹ êm và không thấm nước."),
            new CategorySpec("giay-nu", "Giày nữ thời trang", "Cao gót, búp bê, mũi nhọn thanh lịch", "heel",
                    new String[]{"WOMEN"}, 590_000, 1_790_000,
                    new String[]{"Grace", "Muse", "Bella", "Elegant", "Pointy", "Ballet", "Chic"},
                    "Mũi nhọn thanh thoát, gót vững chắc 5-7 cm, lót đệm êm chân khi đứng lâu."),
            new CategorySpec("giay-da-bong", "Giày đá bóng", "Bám sân, kiểm soát bóng tốt", "football",
                    new String[]{"MEN", "UNISEX"}, 790_000, 2_490_000,
                    new String[]{"Striker", "Turf", "Goal", "Volley", "Pitch", "Derby Pro"},
                    "Đinh TF dành cho sân cỏ nhân tạo, thân giày ôm chân, mũi giày tăng ma sát khi chạm bóng."),
            new CategorySpec("tre-em", "Giày trẻ em", "Nhẹ, êm, dễ mang cho bé", "kids",
                    new String[]{"KIDS"}, 390_000, 1_090_000,
                    new String[]{"Mini", "Junior", "Little", "Kiddo", "Bunny", "Rocket"},
                    "Quai dán tiện lợi để bé tự mang, đế mềm uốn cong theo bước chân, chất liệu an toàn."));

    private static final String[] PROVINCES = {"Hà Nội", "TP. Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ",
            "Bình Dương", "Đồng Nai", "Khánh Hòa", "Thừa Thiên Huế", "Quảng Ninh", "Nghệ An", "Lâm Đồng", "Bắc Ninh",
            "Thanh Hóa", "Bà Rịa - Vũng Tàu"};
    private static final String[] STREETS = {"Lê Lợi", "Trần Hưng Đạo", "Nguyễn Huệ", "Hai Bà Trưng", "Lý Thường Kiệt",
            "Phan Chu Trinh", "Điện Biên Phủ", "Võ Văn Tần", "Nguyễn Trãi", "Quang Trung", "Lê Duẩn", "Bạch Đằng"};
    private static final String[] FAMILY = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng",
            "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý"};
    private static final String[] MIDDLE = {"Văn", "Thị", "Minh", "Ngọc", "Thu", "Đức", "Thanh", "Quang", "Bảo", "Gia"};
    private static final String[] GIVEN = {"An", "Bình", "Chi", "Dũng", "Giang", "Hà", "Hải", "Hùng", "Hương", "Khoa",
            "Lan", "Linh", "Long", "Mai", "Nam", "Nga", "Phúc", "Quân", "Sơn", "Thảo", "Trang", "Trung", "Tuấn", "Vy", "Yến"};

    // ------------------------------------------------------------------ seeding

    private record ProductSeed(long id, String name, BigDecimal price, BigDecimal salePrice, String image,
                               List<long[]> variants /* {variantId, size} */, List<String> sizes) {
    }

    private void seed() {
        Instant now = clock.instant();
        List<ProductSeed> products = seedCatalogue(now);
        List<long[]> customers = seedCustomers(now);
        seedCoupons();
        seedOrders(products, customers, now);
        jdbc.update("""
                update products p set sold_count = COALESCE((
                  select sum(i.quantity) from order_items i join orders o on o.id = i.order_id
                  where i.product_id = p.id and o.status not in ('CANCELLED', 'PAYMENT_FAILED')), 0)""");
        log.info("Seeded {} products, {} customers", products.size(), customers.size());
    }

    private List<ProductSeed> seedCatalogue(Instant now) {
        List<ProductSeed> result = new ArrayList<>();
        int sort = 0;
        for (CategorySpec c : CATEGORIES) {
            Long categoryId = jdbc.queryForObject(
                    "insert into categories (slug, name, description, sort_order) values (?, ?, ?, ?) returning id",
                    Long.class, c.slug(), c.name(), c.description(), sort++);
            int count = 12;
            for (int i = 0; i < count; i++) {
                String brand = BRANDS[random.nextInt(BRANDS.length)];
                String model = c.models()[i % c.models().length] + (i >= c.models().length ? " " + (2 + i / c.models().length) : "");
                ColorSpec color = COLORS[random.nextInt(COLORS.length)];
                String gender = c.genders()[random.nextInt(c.genders().length)];
                String name = brand + " " + model;
                String slug = Slugs.of(name + " " + color.name());
                Integer dup = jdbc.queryForObject("select count(*) from products where slug like ?", Integer.class, slug + "%");
                if (dup != null && dup > 0) {
                    slug = slug + "-" + (dup + 1);
                }
                BigDecimal price = roundPrice(c.minPrice() + random.nextInt(c.maxPrice() - c.minPrice()));
                BigDecimal sale = random.nextInt(4) == 0
                        ? roundPrice(price.multiply(BigDecimal.valueOf(70 + random.nextInt(21))).divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN).intValue())
                        : null;
                if (sale != null && sale.compareTo(price) >= 0) {
                    sale = null;
                }
                String image = "/images/shoes/" + c.style() + "-" + color.key() + ".svg";
                String description = c.blurb() + " Màu " + color.name().toLowerCase() + ". "
                        + "Đổi size miễn phí trong 7 ngày, bảo hành keo đế 6 tháng.";
                BigDecimal rating = BigDecimal.valueOf(38 + random.nextInt(13)).divide(BigDecimal.TEN, 1, RoundingMode.HALF_UP);
                Instant created = now.minus(Duration.ofDays(random.nextInt(150))).minus(Duration.ofMinutes(random.nextInt(1440)));
                Long productId = jdbc.queryForObject("""
                        insert into products (slug, name, brand, category_id, gender, color, description, price, sale_price,
                                              image, active, featured, rating, created_at, updated_at)
                        values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, ?, ?, ?, ?) returning id""", Long.class,
                        slug, name, brand, categoryId, gender, color.name(), description, price, sale, image,
                        random.nextInt(6) == 0, rating, Timestamp.from(created), Timestamp.from(created));
                List<long[]> variants = new ArrayList<>();
                List<String> sizes = sizesFor(gender);
                for (String size : sizes) {
                    int stock = random.nextInt(10) == 0 ? 0 : 2 + random.nextInt(24);
                    Long variantId = jdbc.queryForObject(
                            "insert into product_variants (product_id, size, sku, stock) values (?, ?, ?, ?) returning id",
                            Long.class, productId, size, "SB" + productId + "-" + size, stock);
                    variants.add(new long[]{variantId, Long.parseLong(size)});
                }
                result.add(new ProductSeed(productId, name, price, sale, image, variants, sizes));
            }
        }
        return result;
    }

    private static List<String> sizesFor(String gender) {
        int from;
        int to;
        switch (gender) {
            case "MEN" -> { from = 39; to = 45; }
            case "WOMEN" -> { from = 35; to = 40; }
            case "KIDS" -> { from = 28; to = 35; }
            default -> { from = 36; to = 44; }
        }
        List<String> sizes = new ArrayList<>();
        for (int s = from; s <= to; s++) {
            sizes.add(Integer.toString(s));
        }
        return sizes;
    }

    /** Returns {userId, nameIndex} pairs; the name is regenerated from the same random stream order. */
    private List<long[]> seedCustomers(Instant now) {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        List<long[]> ids = new ArrayList<>();
        Long demo = jdbc.queryForObject("""
                insert into users (email, password_hash, full_name, phone, address, province, role, created_at)
                values ('khach@saibuoc.vn', ?, 'Nguyễn Minh Khách', '0901234567', '12 Lê Lợi, Quận 1', 'TP. Hồ Chí Minh',
                        'CUSTOMER', ?) returning id""", Long.class, hash, Timestamp.from(now.minus(Duration.ofDays(120))));
        ids.add(new long[]{demo});
        names.put(demo, new String[]{"Nguyễn Minh Khách", "0901234567", "12 Lê Lợi, Quận 1", "TP. Hồ Chí Minh", "khach@saibuoc.vn"});
        // Accounts reserved for load tests (their orders are cleaned up by scripts/load/reset-load-data.sql).
        for (int i = 1; i <= 20; i++) {
            String email = "load%02d@saibuoc.vn".formatted(i);
            Long id = jdbc.queryForObject("""
                    insert into users (email, password_hash, full_name, phone, role, created_at)
                    values (?, ?, ?, ?, 'CUSTOMER', ?) returning id""", Long.class, email, hash,
                    "Khách kiểm thử " + i, "09000000%02d".formatted(i), Timestamp.from(now.minus(Duration.ofDays(100))));
            names.put(id, new String[]{"Khách kiểm thử " + i, "09000000%02d".formatted(i), "1 Lê Lợi", "Hà Nội", email});
        }
        for (int i = 0; i < 240; i++) {
            String name = FAMILY[random.nextInt(FAMILY.length)] + " " + MIDDLE[random.nextInt(MIDDLE.length)] + " "
                    + GIVEN[random.nextInt(GIVEN.length)];
            String email = Slugs.of(name).replace("-", ".") + (100 + i) + "@example.com";
            String phone = "09" + (10_000_000 + random.nextInt(89_999_999));
            String province = PROVINCES[random.nextInt(PROVINCES.length)];
            String address = (1 + random.nextInt(300)) + " " + STREETS[random.nextInt(STREETS.length)];
            Instant created = now.minus(Duration.ofDays(random.nextInt(HISTORY_DAYS + 30))).minus(Duration.ofMinutes(random.nextInt(1440)));
            Long id = jdbc.queryForObject("""
                    insert into users (email, password_hash, full_name, phone, address, province, role, created_at, last_login_at)
                    values (?, ?, ?, ?, ?, ?, 'CUSTOMER', ?, ?) returning id""", Long.class, email, hash, name, phone,
                    address, province, Timestamp.from(created), Timestamp.from(created.plus(Duration.ofDays(random.nextInt(20)))));
            ids.add(new long[]{id});
            names.put(id, new String[]{name, phone, address, province, email});
        }
        return ids;
    }

    private final Map<Long, String[]> names = new HashMap<>();

    private void seedCoupons() {
        LocalDate today = LocalDate.now(clock.withZone(OrderService.SHOP_ZONE));
        jdbc.update("""
                insert into coupons (code, description, type, value, min_order, max_discount, starts_on, ends_on, usage_limit, active) values
                ('CHAOBAN', 'Giảm 10% (tối đa 100.000đ) cho đơn từ 500.000đ', 'PERCENT', 10, 500000, 100000, ?, ?, null, true),
                ('GIAM200K', 'Giảm 200.000đ cho đơn từ 2.000.000đ', 'FIXED', 200000, 2000000, null, ?, ?, null, true),
                ('VIP15', 'Giảm 15% (tối đa 300.000đ), số lượng có hạn', 'PERCENT', 15, 1000000, 300000, ?, ?, 500, true),
                ('FREESHIP', 'Giảm 30.000đ phí giao hàng', 'FIXED', 30000, 0, null, ?, ?, null, true),
                ('HE2025', 'Khuyến mãi hè (đã kết thúc)', 'PERCENT', 20, 0, 200000, ?, ?, null, true)""",
                today.minusDays(120), today.plusDays(180), today.minusDays(60), today.plusDays(60),
                today.minusDays(30), today.plusDays(30), today.minusDays(120), today.plusDays(365),
                today.minusDays(200), today.minusDays(100));
    }

    private void seedOrders(List<ProductSeed> products, List<long[]> customers, Instant now) {
        LocalDate today = LocalDate.now(clock.withZone(OrderService.SHOP_ZONE));
        // Popularity weights: a few products sell much more than the rest.
        double[] weights = new double[products.size()];
        double sum = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.pow(random.nextDouble(), 3) * 10 + 0.3;
            sum += weights[i];
        }
        List<Object[]> orderRows = new ArrayList<>();
        List<Object[]> itemRows = new ArrayList<>();
        List<Object[]> historyRows = new ArrayList<>();
        Map<String, Integer> couponUse = new LinkedHashMap<>();
        for (int day = HISTORY_DAYS; day >= 0; day--) {
            LocalDate date = today.minusDays(day);
            int weekday = date.getDayOfWeek().getValue();
            int base = 14 + (HISTORY_DAYS - day) / 6 + (weekday >= 5 ? 8 : 0);
            int count = day == 0 ? Math.max(3, base * nowFractionOfDay(now) / 100) : base + random.nextInt(10);
            for (int n = 0; n < count; n++) {
                Instant created = date.atStartOfDay(OrderService.SHOP_ZONE).toInstant()
                        .plus(Duration.ofMinutes(7 * 60 + random.nextInt(16 * 60)));
                if (created.isAfter(now)) {
                    created = now.minus(Duration.ofMinutes(1 + random.nextInt(60)));
                }
                long orderId = jdbc.queryForObject("select nextval('orders_id_seq')", Long.class);
                String code = "SB" + jdbc.queryForObject("select nextval('order_code_seq')", Long.class);
                boolean registered = random.nextInt(10) < 6;
                long[] customer = customers.get(random.nextInt(customers.size()));
                String[] info = registered ? names.get(customer[0]) : guest();
                int lines = random.nextInt(10) < 7 ? 1 : 2 + random.nextInt(2);
                BigDecimal subtotal = BigDecimal.ZERO;
                for (int l = 0; l < lines; l++) {
                    ProductSeed p = products.get(pick(weights, sum));
                    long[] variant = p.variants().get(random.nextInt(p.variants().size()));
                    int qty = random.nextInt(10) == 0 ? 2 : 1;
                    BigDecimal unit = p.salePrice() != null ? p.salePrice() : p.price();
                    BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty));
                    subtotal = subtotal.add(lineTotal);
                    itemRows.add(new Object[]{orderId, p.id(), variant[0], p.name(), Long.toString(variant[1]), p.image(),
                            unit, qty, lineTotal});
                }
                String coupon = null;
                BigDecimal discount = BigDecimal.ZERO;
                if (random.nextInt(6) == 0 && subtotal.compareTo(BigDecimal.valueOf(500_000)) >= 0) {
                    coupon = "CHAOBAN";
                    discount = subtotal.divide(BigDecimal.TEN, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(100_000));
                } else if (random.nextInt(8) == 0 && subtotal.compareTo(BigDecimal.valueOf(2_000_000)) >= 0) {
                    coupon = "GIAM200K";
                    discount = BigDecimal.valueOf(200_000);
                }
                BigDecimal after = subtotal.subtract(discount);
                BigDecimal shipping = after.compareTo(props.shipping().freeFrom()) >= 0 ? BigDecimal.ZERO : props.shipping().fee();
                boolean card = random.nextInt(10) < 4;
                String[] outcome = outcome(day, card);
                String status = outcome[0];
                String paymentStatus = outcome[1];
                if (!"PAYMENT_FAILED".equals(status) && !"CANCELLED".equals(status) && coupon != null) {
                    couponUse.merge(coupon, 1, Integer::sum);
                }
                Instant updated = created.plus(Duration.ofHours(Math.min(day * 24L, 2 + random.nextInt(72))));
                if (updated.isAfter(now)) {
                    updated = now;
                }
                orderRows.add(new Object[]{orderId, code, registered ? customer[0] : null, info[0], info[1],
                        info[4], info[2], info[3], status, card ? "CARD" : "COD", paymentStatus,
                        card && !"FAILED".equals(paymentStatus) ? "PAY-" + Long.toHexString(orderId * 7919L).toUpperCase() : null,
                        "PAYMENT_FAILED".equals(status) ? "Thẻ bị từ chối bởi ngân hàng phát hành" : null,
                        subtotal, discount, shipping, after.add(shipping), coupon, Timestamp.from(created),
                        Timestamp.from(updated)});
                historyRows.add(new Object[]{orderId, null, "PENDING", "Đặt hàng", registered ? info[4] : "Khách vãng lai",
                        Timestamp.from(created)});
                String prev = "PENDING";
                Instant t = created;
                for (String step : path(status)) {
                    t = t.plus(Duration.ofHours(2 + random.nextInt(30)));
                    if (t.isAfter(now)) {
                        t = now;
                    }
                    historyRows.add(new Object[]{orderId, prev, step, null,
                            "PAYMENT_FAILED".equals(step) ? "Cổng thanh toán" : "admin@saibuoc.vn", Timestamp.from(t)});
                    prev = step;
                }
            }
        }
        jdbc.batchUpdate("""
                insert into orders (id, code, user_id, customer_name, phone, email, address, province, status,
                                    payment_method, payment_status, payment_ref, payment_error, subtotal, discount,
                                    shipping_fee, total, coupon_code, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", orderRows);
        jdbc.batchUpdate("""
                insert into order_items (order_id, product_id, variant_id, product_name, size, image, unit_price, quantity, line_total)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)""", itemRows);
        jdbc.batchUpdate("""
                insert into order_history (order_id, from_status, to_status, note, changed_by, changed_at)
                values (?, ?, ?, ?, ?, ?)""", historyRows);
        couponUse.forEach((code, n) -> jdbc.update("update coupons set used_count = ? where code = ?", n, code));
        log.info("Seeded {} orders with {} items", orderRows.size(), itemRows.size());
    }

    /** {status, paymentStatus} depending on how old the order is. */
    private String[] outcome(int ageDays, boolean card) {
        int roll = random.nextInt(100);
        if (card && roll < 6) {
            return new String[]{"PAYMENT_FAILED", "FAILED"};
        }
        String paid = card ? "PAID" : "UNPAID";
        if (roll < 13) {
            return new String[]{"CANCELLED", card ? "REFUNDED" : "UNPAID"};
        }
        if (ageDays == 0) {
            return new String[]{roll < 75 ? "PENDING" : "CONFIRMED", paid};
        }
        if (ageDays <= 2) {
            return new String[]{roll < 35 ? "PENDING" : roll < 70 ? "CONFIRMED" : "SHIPPING", paid};
        }
        if (ageDays <= 5) {
            return new String[]{roll < 45 ? "SHIPPING" : "DELIVERED", roll < 45 ? paid : "PAID"};
        }
        return new String[]{"DELIVERED", "PAID"};
    }

    private static List<String> path(String status) {
        return switch (status) {
            case "CONFIRMED" -> List.of("CONFIRMED");
            case "SHIPPING" -> List.of("CONFIRMED", "SHIPPING");
            case "DELIVERED" -> List.of("CONFIRMED", "SHIPPING", "DELIVERED");
            case "CANCELLED" -> List.of("CANCELLED");
            case "PAYMENT_FAILED" -> List.of("PAYMENT_FAILED");
            default -> List.of();
        };
    }

    private String[] guest() {
        String name = FAMILY[random.nextInt(FAMILY.length)] + " " + MIDDLE[random.nextInt(MIDDLE.length)] + " "
                + GIVEN[random.nextInt(GIVEN.length)];
        return new String[]{name, "03" + (10_000_000 + random.nextInt(89_999_999)),
                (1 + random.nextInt(300)) + " " + STREETS[random.nextInt(STREETS.length)],
                PROVINCES[random.nextInt(PROVINCES.length)], random.nextBoolean() ? null : Slugs.of(name).replace("-", "") + "@gmail.com"};
    }

    private int pick(double[] weights, double sum) {
        double r = random.nextDouble() * sum;
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r <= 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    private int nowFractionOfDay(Instant now) {
        long minutes = ChronoUnit.MINUTES.between(LocalDate.now(clock.withZone(OrderService.SHOP_ZONE))
                .atStartOfDay(OrderService.SHOP_ZONE).toInstant(), now);
        return (int) Math.min(100, minutes * 100 / 1440);
    }

    /** 1.234.567 -> 1.229.000 style retail prices (x9.000). */
    private static BigDecimal roundPrice(int value) {
        int thousands = value / 10_000 * 10 + 9;
        return BigDecimal.valueOf(thousands * 1_000L);
    }
}
