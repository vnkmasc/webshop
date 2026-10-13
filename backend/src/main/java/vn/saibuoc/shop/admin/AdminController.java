package vn.saibuoc.shop.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.saibuoc.shop.common.ApiResponse;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.common.PageResponse;
import vn.saibuoc.shop.order.Order;
import vn.saibuoc.shop.order.OrderService;
import vn.saibuoc.shop.payment.PaymentClient;
import vn.saibuoc.shop.security.SecurityUtils;

/** Admin API (role ADMIN, enforced in SecurityConfig for /api/admin/**). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboard;
    private final AdminProductService products;
    private final AdminQueryService queries;
    private final OrderService orders;
    private final PaymentClient paymentClient;

    public record StatusRequest(@NotNull Order.Status status, String note) {
    }

    public record AdminOrder(OrderService.OrderDto order, List<String> nextStatuses) {
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardService.Dashboard> dashboard(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(dashboard.dashboard(days));
    }

    // ------------------------------------------------------------------ products

    @GetMapping("/products")
    public ApiResponse<PageResponse<AdminProductService.ProductRow>> products(
            @RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "false") boolean lowStock,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(products.list(q, categoryId, active, lowStock, page, size));
    }

    @GetMapping("/products/{id}")
    public ApiResponse<AdminProductService.ProductDto> product(@PathVariable long id) {
        return ApiResponse.ok(products.get(id));
    }

    @PostMapping("/products")
    public ApiResponse<AdminProductService.ProductDto> create(@Valid @RequestBody AdminProductService.ProductRequest req) {
        return ApiResponse.ok(products.create(req), "Đã tạo sản phẩm");
    }

    @PutMapping("/products/{id}")
    public ApiResponse<AdminProductService.ProductDto> update(@PathVariable long id,
                                                              @Valid @RequestBody AdminProductService.ProductRequest req) {
        return ApiResponse.ok(products.update(id, req), "Đã lưu sản phẩm");
    }

    @PutMapping("/products/{id}/stock")
    public ApiResponse<AdminProductService.ProductDto> stock(@PathVariable long id,
                                                             @Valid @RequestBody AdminProductService.StockRequest req) {
        return ApiResponse.ok(products.setStock(id, req), "Đã cập nhật tồn kho");
    }

    // ------------------------------------------------------------------ orders

    @GetMapping("/orders")
    public ApiResponse<PageResponse<AdminQueryService.OrderRow>> orders(
            @RequestParam(required = false) String status, @RequestParam(required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String payment,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queries.orders(status, q, from, to, payment, page, size));
    }

    @GetMapping("/orders/status-counts")
    public ApiResponse<List<AdminQueryService.StatusCount>> statusCounts() {
        return ApiResponse.ok(queries.orderStatusCounts());
    }

    @GetMapping("/orders/{code}")
    public ApiResponse<AdminOrder> order(@PathVariable String code) {
        return ApiResponse.ok(withNext(orders.adminGet(code)));
    }

    @PostMapping("/orders/{code}/status")
    public ApiResponse<AdminOrder> changeStatus(@PathVariable String code, @Valid @RequestBody StatusRequest req) {
        return ApiResponse.ok(withNext(orders.changeStatus(code, req.status(), req.note(),
                SecurityUtils.currentUser().email())), "Đã cập nhật đơn hàng");
    }

    private static AdminOrder withNext(OrderService.OrderDto o) {
        return new AdminOrder(o, OrderService.nextStatuses(Order.Status.valueOf(o.status())).stream()
                .map(Enum::name).sorted().toList());
    }

    // ------------------------------------------------------------------ customers and coupons

    @GetMapping("/customers")
    public ApiResponse<PageResponse<AdminQueryService.CustomerRow>> customers(
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(queries.customers(q, page, size));
    }

    @GetMapping("/coupons")
    public ApiResponse<List<AdminQueryService.CouponDto>> coupons() {
        return ApiResponse.ok(queries.coupons());
    }

    @PostMapping("/coupons")
    public ApiResponse<AdminQueryService.CouponDto> createCoupon(@Valid @RequestBody AdminQueryService.CouponRequest req) {
        return ApiResponse.ok(queries.createCoupon(req), "Đã tạo mã giảm giá");
    }

    @PutMapping("/coupons/{id}")
    public ApiResponse<AdminQueryService.CouponDto> updateCoupon(@PathVariable long id,
                                                                 @Valid @RequestBody AdminQueryService.CouponRequest req) {
        return ApiResponse.ok(queries.updateCoupon(id, req), "Đã lưu mã giảm giá");
    }

    // ------------------------------------------------------------------ payment gateway behaviour

    @GetMapping("/payment-gateway")
    public ApiResponse<Map<String, Object>> gateway() {
        try {
            return ApiResponse.ok(paymentClient.settings());
        } catch (PaymentClient.GatewayException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "GATEWAY_UNREACHABLE", "Không kết nối được cổng thanh toán");
        }
    }

    @PutMapping("/payment-gateway")
    public ApiResponse<Map<String, Object>> updateGateway(@RequestBody Map<String, Object> settings) {
        try {
            return ApiResponse.ok(paymentClient.updateSettings(settings), "Đã lưu cấu hình cổng thanh toán");
        } catch (PaymentClient.GatewayException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "GATEWAY_UNREACHABLE", "Không kết nối được cổng thanh toán");
        }
    }
}
