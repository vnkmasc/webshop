package vn.saibuoc.shop.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.saibuoc.shop.catalog.Category;
import vn.saibuoc.shop.catalog.CategoryRepository;
import vn.saibuoc.shop.catalog.Product;
import vn.saibuoc.shop.catalog.ProductRepository;
import vn.saibuoc.shop.catalog.ProductVariant;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.common.PageResponse;
import vn.saibuoc.shop.common.Slugs;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminProductService {

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public record ProductRow(long id, String slug, String name, String brand, String category, String gender,
                             String color, BigDecimal price, BigDecimal salePrice, String image, boolean active,
                             boolean featured, int soldCount, long totalStock, long sizesOutOfStock) {
    }

    public record VariantDto(Long id, String size, String sku, int stock) {
    }

    public record ProductDto(long id, String slug, String name, String brand, long categoryId, String gender,
                             String color, String description, BigDecimal price, BigDecimal salePrice, String image,
                             boolean active, boolean featured, BigDecimal rating, int soldCount, Instant createdAt,
                             List<VariantDto> variants) {
    }

    public record ProductRequest(
            @NotBlank @Size(max = 140) String name,
            @NotBlank @Size(max = 60) String brand,
            @NotNull Long categoryId,
            @NotNull Product.Gender gender,
            @NotBlank @Size(max = 40) String color,
            @Size(max = 5000) String description,
            @NotNull @Positive BigDecimal price,
            @Positive BigDecimal salePrice,
            @NotBlank @Size(max = 200) String image,
            boolean active,
            boolean featured,
            @DecimalMin("0") @DecimalMax("5") BigDecimal rating) {
    }

    public record StockLine(@NotBlank @Pattern(regexp = "^\\d{2}$", message = "Size gồm 2 chữ số") String size,
                            @Min(0) @Max(9999) int stock) {
    }

    public record StockRequest(@NotNull @Size(min = 1, max = 20) List<@Valid StockLine> lines) {
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductRow> list(String q, Long categoryId, Boolean active, boolean lowStock, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 200);
        MapSqlParameterSource p = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder("TRUE");
        if (q != null && !q.isBlank()) {
            where.append(" AND (p.name ILIKE :q OR p.brand ILIKE :q OR p.slug LIKE :qslug OR EXISTS"
                    + " (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.sku ILIKE :q))");
            p.addValue("q", "%" + q.trim() + "%").addValue("qslug", "%" + Slugs.of(q) + "%");
        }
        if (categoryId != null) {
            where.append(" AND p.category_id = :categoryId");
            p.addValue("categoryId", categoryId);
        }
        if (active != null) {
            where.append(" AND p.active = :active");
            p.addValue("active", active);
        }
        if (lowStock) {
            where.append(" AND EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.stock <= 3)");
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM products p WHERE " + where, p, Long.class);
        p.addValue("limit", pageSize).addValue("offset", (long) Math.max(page, 0) * pageSize);
        List<ProductRow> rows = jdbc.query("""
                SELECT p.id, p.slug, p.name, p.brand, c.name AS category, p.gender, p.color, p.price, p.sale_price,
                       p.image, p.active, p.featured, p.sold_count,
                       COALESCE(sum(v.stock), 0) AS total_stock, count(v.id) FILTER (WHERE v.stock = 0) AS sizes_out
                FROM products p JOIN categories c ON c.id = p.category_id
                LEFT JOIN product_variants v ON v.product_id = p.id
                WHERE %s GROUP BY p.id, c.name ORDER BY p.id DESC LIMIT :limit OFFSET :offset""".formatted(where), p,
                (rs, i) -> new ProductRow(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                        rs.getString("brand"), rs.getString("category"), rs.getString("gender"), rs.getString("color"),
                        rs.getBigDecimal("price"), rs.getBigDecimal("sale_price"), rs.getString("image"),
                        rs.getBoolean("active"), rs.getBoolean("featured"), rs.getInt("sold_count"),
                        rs.getLong("total_stock"), rs.getLong("sizes_out")));
        return PageResponse.of(rows, Math.max(page, 0), pageSize, total);
    }

    @Transactional(readOnly = true)
    public ProductDto get(long id) {
        return toDto(load(id));
    }

    @Transactional
    public ProductDto create(ProductRequest req) {
        validatePrices(req);
        Product p = new Product();
        apply(p, req);
        String base = Slugs.of(req.name() + " " + req.color());
        String slug = base;
        for (int i = 2; products.existsBySlug(slug); i++) {
            slug = base + "-" + i;
        }
        p.setSlug(slug);
        p.setCreatedAt(clock.instant());
        products.save(p);
        log.info("Product created id={} slug={}", p.getId(), slug);
        return toDto(p);
    }

    @Transactional
    public ProductDto update(long id, ProductRequest req) {
        validatePrices(req);
        Product p = load(id);
        apply(p, req);
        log.info("Product updated id={}", id);
        return toDto(p);
    }

    /** Sets the stock of each listed size; sizes not yet present are added. Sizes are never deleted. */
    @Transactional
    public ProductDto setStock(long id, StockRequest req) {
        Product p = load(id);
        Map<String, ProductVariant> bySize = p.getVariants().stream()
                .collect(Collectors.toMap(ProductVariant::getSize, Function.identity()));
        for (StockLine line : req.lines()) {
            ProductVariant v = bySize.get(line.size());
            if (v == null) {
                v = new ProductVariant();
                v.setProduct(p);
                v.setSize(line.size());
                v.setSku(("SB" + p.getId() + "-" + line.size()));
                p.getVariants().add(v);
                bySize.put(line.size(), v);
            }
            v.setStock(line.stock());
        }
        p.setUpdatedAt(clock.instant());
        products.saveAndFlush(p);
        log.info("Stock updated for product id={}", id);
        return toDto(p);
    }

    private void apply(Product p, ProductRequest req) {
        Category c = categories.findById(req.categoryId()).orElseThrow(() -> BusinessException.notFound("danh mục"));
        p.setName(req.name().trim());
        p.setBrand(req.brand().trim());
        p.setCategory(c);
        p.setGender(req.gender());
        p.setColor(req.color().trim());
        p.setDescription(req.description() == null ? "" : req.description().trim());
        p.setPrice(req.price());
        p.setSalePrice(req.salePrice());
        p.setImage(req.image().trim());
        p.setActive(req.active());
        p.setFeatured(req.featured());
        if (req.rating() != null) {
            p.setRating(req.rating());
        }
        p.setUpdatedAt(clock.instant());
    }

    private static void validatePrices(ProductRequest req) {
        if (req.salePrice() != null && req.salePrice().compareTo(req.price()) >= 0) {
            throw BusinessException.badRequest("SALE_PRICE_INVALID", "Giá khuyến mãi phải thấp hơn giá gốc");
        }
    }

    private Product load(long id) {
        return products.findById(id).orElseThrow(() -> BusinessException.notFound("sản phẩm"));
    }

    private static ProductDto toDto(Product p) {
        List<VariantDto> variants = p.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getSize))
                .map(v -> new VariantDto(v.getId(), v.getSize(), v.getSku(), v.getStock())).toList();
        return new ProductDto(p.getId(), p.getSlug(), p.getName(), p.getBrand(), p.getCategory().getId(),
                p.getGender().name(), p.getColor(), p.getDescription(), p.getPrice(), p.getSalePrice(), p.getImage(),
                p.isActive(), p.isFeatured(), p.getRating(), p.getSoldCount(), p.getCreatedAt(), variants);
    }
}
