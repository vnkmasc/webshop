package vn.saibuoc.shop.order;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.saibuoc.shop.common.ApiResponse;
import vn.saibuoc.shop.security.SecurityUtils;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orders;

    @PostMapping("/api/cart/quote")
    public ApiResponse<OrderService.Quote> quote(@Valid @RequestBody OrderService.QuoteRequest req) {
        return ApiResponse.ok(orders.quote(req));
    }

    /** Guests may order too; a logged-in customer's order is linked to the account. */
    @PostMapping("/api/orders")
    public ApiResponse<OrderService.OrderDto> place(@Valid @RequestBody OrderService.PlaceOrderRequest req) {
        return ApiResponse.ok(orders.place(req, SecurityUtils.currentUserOptional().orElse(null)), "Đặt hàng thành công");
    }

    @GetMapping("/api/orders/lookup")
    public ApiResponse<OrderService.OrderDto> lookup(@RequestParam String code, @RequestParam String phone) {
        return ApiResponse.ok(orders.lookup(code, phone));
    }

    @GetMapping("/api/account/orders")
    public ApiResponse<List<OrderService.OrderSummary>> mine() {
        return ApiResponse.ok(orders.myOrders(SecurityUtils.currentUser()));
    }

    @GetMapping("/api/account/orders/{code}")
    public ApiResponse<OrderService.OrderDto> mine(@PathVariable String code) {
        return ApiResponse.ok(orders.myOrder(code, SecurityUtils.currentUser()));
    }

    @PostMapping("/api/account/orders/{code}/cancel")
    public ApiResponse<OrderService.OrderDto> cancel(@PathVariable String code) {
        return ApiResponse.ok(orders.cancelByCustomer(code, SecurityUtils.currentUser()), "Đã hủy đơn hàng");
    }
}
