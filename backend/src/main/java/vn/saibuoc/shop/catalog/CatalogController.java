package vn.saibuoc.shop.catalog;

import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.saibuoc.shop.common.ApiResponse;
import vn.saibuoc.shop.common.PageResponse;

@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalog;

    @GetMapping("/api/home")
    public ApiResponse<CatalogService.Home> home() {
        return ApiResponse.ok(catalog.home());
    }

    @GetMapping("/api/catalog/categories")
    public ApiResponse<List<CatalogService.CategoryDto>> categories() {
        return ApiResponse.ok(catalog.categories());
    }

    @GetMapping("/api/catalog/facets")
    public ApiResponse<CatalogService.Facets> facets() {
        return ApiResponse.ok(catalog.facets());
    }

    @GetMapping("/api/products")
    public ApiResponse<PageResponse<CatalogService.ProductCard>> products(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> brand,
            @RequestParam(required = false) List<String> gender,
            @RequestParam(required = false) List<String> size,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "false") boolean onSale,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int pageSize) {
        var filter = new CatalogService.Filter(category, brand, gender, size, minPrice, maxPrice, q, onSale, sort);
        return ApiResponse.ok(catalog.search(filter, page, pageSize));
    }

    @GetMapping("/api/products/{slug}")
    public ApiResponse<CatalogService.ProductDetail> product(@PathVariable String slug) {
        return ApiResponse.ok(catalog.detail(slug));
    }
}
