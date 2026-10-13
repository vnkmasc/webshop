package vn.saibuoc.shop.catalog;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    public enum Gender { MEN, WOMEN, UNISEX, KIDS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String brand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Gender gender;

    @Column(nullable = false)
    private String color;

    @Column(nullable = false)
    private String description = "";

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "sale_price")
    private BigDecimal salePrice;

    @Column(nullable = false)
    private String image;

    private boolean active = true;
    private boolean featured;

    @Column(name = "sold_count")
    private int soldCount;

    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private int version;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    @OrderBy("size")
    private List<ProductVariant> variants = new ArrayList<>();

    /** Price the customer pays now. */
    public BigDecimal effectivePrice() {
        return salePrice != null && salePrice.compareTo(price) < 0 ? salePrice : price;
    }
}
