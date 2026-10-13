package vn.saibuoc.shop.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.common.PageResponse;
import vn.saibuoc.shop.common.Slugs;
import vn.saibuoc.shop.simulation.SimulationService;

/** Read side of the storefront: plain SQL so every query is explicit and index-friendly. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogService {

    public static final int MAX_PAGE_SIZE = 60;
    private static final Duration NEW_FOR = Duration.ofDays(30);

    private final NamedParameterJdbcTemplate jdbc;
    private final SimulationService simulation;
    private final Clock clock;

    public record ProductCard(long id, String slug, String name, String brand, String categorySlug, String categoryName,
                              String gender, String color, BigDecimal price, BigDecimal salePrice, String image,
                              BigDecimal rating, int soldCount, boolean isNew, List<String> sizes) {
        @JsonProperty("inStock")
        public boolean inStock() {
            return !sizes.isEmpty();
        }
    }

    public record VariantDto(long id, String size, String sku, int stock) {
    }

    public record ProductDetail(ProductCard product, String description, List<VariantDto> variants,
                                List<ProductCard> related) {
    }

    public record CategoryDto(long id, String slug, String name, String description, long productCount) {
    }

    public record Facets(List<String> brands, List<String> sizes, List<String> colors, BigDecimal minPrice,
                         BigDecimal maxPrice) {
    }

    public record Home(List<CategoryDto> categories, List<ProductCard> featured, List<ProductCard> bestsellers,
                       List<ProductCard> newest, List<ProductCard> onSale) {
    }

    public record Filter(String category, List<String> brands, List<String> genders, List<String> sizes,
                         BigDecimal minPrice, BigDecimal maxPrice, String q, boolean onSale, String sort) {
    }

    private static final String CARD_COLUMNS = """
            p.id, p.slug, p.name, p.brand, c.slug AS category_slug, c.name AS category_name, p.gender, p.color,
            p.price, p.sale_price, p.image, p.rating, p.sold_count, p.created_at""";

    private static final String SIZES_COLUMN = """
            , COALESCE((SELECT array_agg(v.size ORDER BY v.size) FROM product_variants v
                        WHERE v.product_id = p.id AND v.stock > 0), '{}') AS sizes""";

    public Home home() {
        return new Home(categories(),
                cards("p.active AND p.featured", "p.sold_count DESC, p.id", 8),
                cards("p.active", "p.sold_count DESC, p.id", 8),
                cards("p.active", "p.created_at DESC, p.id DESC", 8),
                cards("p.active AND p.sale_price IS NOT NULL", "(p.price - p.sale_price) DESC, p.id", 8));
    }

    public List<CategoryDto> categories() {
        return jdbc.query("""
                SELECT c.id, c.slug, c.name, c.description, count(p.id) AS product_count
                FROM categories c LEFT JOIN products p ON p.category_id = c.id AND p.active
                GROUP BY c.id ORDER BY c.sort_order, c.id""", (rs, i) -> new CategoryDto(rs.getLong("id"),
                rs.getString("slug"), rs.getString("name"), rs.getString("description"), rs.getLong("product_count")));
    }

    public Facets facets() {
        List<String> brands = jdbc.queryForList("SELECT DISTINCT brand FROM products WHERE active ORDER BY brand",
                new MapSqlParameterSource(), String.class);
        List<String> sizes = new ArrayList<>(jdbc.queryForList("""
                SELECT DISTINCT v.size FROM product_variants v JOIN products p ON p.id = v.product_id
                WHERE p.active AND v.stock > 0""", new MapSqlParameterSource(), String.class));
        sizes.sort(Comparator.comparingInt(CatalogService::sizeOrder));
        List<String> colors = jdbc.queryForList("SELECT DISTINCT color FROM products WHERE active ORDER BY color",
                new MapSqlParameterSource(), String.class);
        var range = jdbc.queryForMap("""
                SELECT min(COALESCE(sale_price, price)) AS lo, max(COALESCE(sale_price, price)) AS hi
                FROM products WHERE active""", new MapSqlParameterSource());
        return new Facets(brands, sizes, colors, (BigDecimal) range.get("lo"), (BigDecimal) range.get("hi"));
    }

    public PageResponse<ProductCard> search(Filter f, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int pageNo = Math.max(page, 0);
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("p.active");
        if (notBlank(f.category())) {
            where.append(" AND c.slug = :category");
            params.addValue("category", f.category());
        }
        if (notEmpty(f.brands())) {
            where.append(" AND p.brand IN (:brands)");
            params.addValue("brands", f.brands());
        }
        if (notEmpty(f.genders())) {
            List<String> genders = new ArrayList<>(f.genders());
            if (genders.contains("MEN") || genders.contains("WOMEN")) {
                genders.add("UNISEX");
            }
            where.append(" AND p.gender IN (:genders)");
            params.addValue("genders", genders);
        }
        if (f.minPrice() != null) {
            where.append(" AND COALESCE(p.sale_price, p.price) >= :minPrice");
            params.addValue("minPrice", f.minPrice());
        }
        if (f.maxPrice() != null) {
            where.append(" AND COALESCE(p.sale_price, p.price) <= :maxPrice");
            params.addValue("maxPrice", f.maxPrice());
        }
        if (notEmpty(f.sizes())) {
            where.append(" AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id"
                    + " AND v.size IN (:sizes) AND v.stock > 0)");
            params.addValue("sizes", f.sizes());
        }
        if (notBlank(f.q())) {
            where.append(" AND (p.name ILIKE :q OR p.brand ILIKE :q OR p.color ILIKE :q OR c.name ILIKE :q"
                    + " OR p.slug LIKE :qslug OR c.slug LIKE :qslug)");
            params.addValue("q", "%" + f.q().trim() + "%");
            params.addValue("qslug", "%" + Slugs.of(f.q()) + "%");
        }
        if (f.onSale()) {
            where.append(" AND p.sale_price IS NOT NULL");
        }
        simulation.slowQueryIfActive();
        long total = jdbc.queryForObject("SELECT count(*) FROM products p JOIN categories c ON c.id = p.category_id WHERE "
                + where, params, Long.class);
        params.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        String order = switch (f.sort() == null ? "" : f.sort()) {
            case "price_asc" -> "COALESCE(p.sale_price, p.price) ASC, p.id";
            case "price_desc" -> "COALESCE(p.sale_price, p.price) DESC, p.id";
            case "bestseller" -> "p.sold_count DESC, p.id";
            case "rating" -> "p.rating DESC, p.sold_count DESC, p.id";
            default -> "p.created_at DESC, p.id DESC";
        };
        List<ProductCard> content;
        if (simulation.nPlusOneActive()) {
            // Deliberately inefficient: one extra query per product (N+1).
            content = jdbc.query("SELECT " + CARD_COLUMNS + " FROM products p JOIN categories c ON c.id = p.category_id"
                    + " WHERE " + where + " ORDER BY " + order + " LIMIT :limit OFFSET :offset", params,
                    (rs, i) -> card(rs, List.of()));
            content = content.stream().map(this::withSizesLoadedOneByOne).toList();
        } else {
            content = jdbc.query("SELECT " + CARD_COLUMNS + SIZES_COLUMN + " FROM products p"
                    + " JOIN categories c ON c.id = p.category_id WHERE " + where + " ORDER BY " + order
                    + " LIMIT :limit OFFSET :offset", params, (rs, i) -> card(rs, sizes(rs)));
        }
        return PageResponse.of(content, pageNo, pageSize, total);
    }

    public ProductDetail detail(String slug) {
        List<ProductCard> found = jdbc.query("SELECT " + CARD_COLUMNS + SIZES_COLUMN + ", p.description"
                        + " FROM products p JOIN categories c ON c.id = p.category_id WHERE p.slug = :slug AND p.active",
                new MapSqlParameterSource("slug", slug), (rs, i) -> card(rs, sizes(rs)));
        if (found.isEmpty()) {
            throw BusinessException.notFound("sản phẩm");
        }
        ProductCard card = found.get(0);
        String description = jdbc.queryForObject("SELECT description FROM products WHERE id = :id",
                new MapSqlParameterSource("id", card.id()), String.class);
        List<VariantDto> variants = new ArrayList<>(jdbc.query(
                "SELECT id, size, sku, stock FROM product_variants WHERE product_id = :id",
                new MapSqlParameterSource("id", card.id()),
                (rs, i) -> new VariantDto(rs.getLong("id"), rs.getString("size"), rs.getString("sku"), rs.getInt("stock"))));
        variants.sort(Comparator.comparingInt(v -> sizeOrder(v.size())));
        return new ProductDetail(card, description, variants, related(card));
    }

    /** "Customers also viewed": same category first, then same brand, ranked by sales. */
    private List<ProductCard> related(ProductCard p) {
        simulation.burnCpuIfActive();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", p.id()).addValue("category", p.categorySlug()).addValue("brand", p.brand());
        return jdbc.query("SELECT " + CARD_COLUMNS + SIZES_COLUMN + " FROM products p"
                + " JOIN categories c ON c.id = p.category_id WHERE p.active AND p.id <> :id"
                + " AND (c.slug = :category OR p.brand = :brand)"
                + " ORDER BY (c.slug = :category) DESC, p.sold_count DESC LIMIT 4", params, (rs, i) -> card(rs, sizes(rs)));
    }

    private List<ProductCard> cards(String where, String order, int limit) {
        return jdbc.query("SELECT " + CARD_COLUMNS + SIZES_COLUMN + " FROM products p JOIN categories c"
                + " ON c.id = p.category_id WHERE " + where + " ORDER BY " + order + " LIMIT :limit",
                new MapSqlParameterSource("limit", limit), (rs, i) -> card(rs, sizes(rs)));
    }

    private ProductCard withSizesLoadedOneByOne(ProductCard c) {
        List<String> sizes = jdbc.queryForList(
                "SELECT size FROM product_variants WHERE product_id = :id AND stock > 0 ORDER BY size",
                new MapSqlParameterSource("id", c.id()), String.class);
        return new ProductCard(c.id(), c.slug(), c.name(), c.brand(), c.categorySlug(), c.categoryName(), c.gender(),
                c.color(), c.price(), c.salePrice(), c.image(), c.rating(), c.soldCount(), c.isNew(), sizes);
    }

    private ProductCard card(ResultSet rs, List<String> sizes) throws SQLException {
        Instant created = rs.getTimestamp("created_at").toInstant();
        return new ProductCard(rs.getLong("id"), rs.getString("slug"), rs.getString("name"), rs.getString("brand"),
                rs.getString("category_slug"), rs.getString("category_name"), rs.getString("gender"),
                rs.getString("color"), rs.getBigDecimal("price"), rs.getBigDecimal("sale_price"), rs.getString("image"),
                rs.getBigDecimal("rating"), rs.getInt("sold_count"), created.isAfter(clock.instant().minus(NEW_FOR)),
                sizes);
    }

    private static List<String> sizes(ResultSet rs) throws SQLException {
        Array a = rs.getArray("sizes");
        if (a == null) {
            return List.of();
        }
        List<String> list = new ArrayList<>(Arrays.asList((String[]) a.getArray()));
        list.sort(Comparator.comparingInt(CatalogService::sizeOrder));
        return list;
    }

    /** Numeric sizes sort numerically ("35" < "100"); anything else goes last. */
    static int sizeOrder(String size) {
        try {
            return Integer.parseInt(size);
        } catch (NumberFormatException e) {
            return 1000;
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean notEmpty(List<String> l) {
        return l != null && !l.isEmpty();
    }

}
