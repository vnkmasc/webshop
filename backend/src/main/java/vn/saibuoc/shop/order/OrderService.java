package vn.saibuoc.shop.order;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.saibuoc.shop.catalog.ProductVariant;
import vn.saibuoc.shop.catalog.ProductVariantRepository;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.config.AppProperties;
import vn.saibuoc.shop.payment.PaymentClient;
import vn.saibuoc.shop.security.CurrentUser;
import vn.saibuoc.shop.simulation.SimulationService;

/**
 * Cart pricing, checkout and the order life cycle.
 * Checkout reserves stock in one transaction, charges the card outside any transaction (a slow gateway must not
 * hold row locks or DB connections) and then records the payment outcome in a second transaction.
 */
@Slf4j
@Service
public class OrderService {

    public static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_LINES = 20;
    static final int MAX_QTY = 10;

    private final OrderRepository orders;
    private final ProductVariantRepository variants;
    private final CouponRepository coupons;
    private final PaymentClient paymentClient;
    private final TransactionTemplate tx;
    private final AppProperties props;
    private final Clock clock;
    private final MeterRegistry meters;
    private final DistributionSummary orderValue;
    private final SimulationService simulation;

    public OrderService(OrderRepository orders, ProductVariantRepository variants, CouponRepository coupons,
                        PaymentClient paymentClient, TransactionTemplate tx, AppProperties props, Clock clock,
                        MeterRegistry meters, SimulationService simulation) {
        this.simulation = simulation;
        this.orders = orders;
        this.variants = variants;
        this.coupons = coupons;
        this.paymentClient = paymentClient;
        this.tx = tx;
        this.props = props;
        this.clock = clock;
        this.meters = meters;
        this.orderValue = DistributionSummary.builder("shop.order.value").baseUnit("VND")
                .description("Value of placed orders").register(meters);
    }

    // ------------------------------------------------------------------ DTOs

    public record CartItem(@NotNull Long variantId, @Min(1) @Max(MAX_QTY) int quantity) {
    }

    public record QuoteRequest(@NotNull @Size(max = MAX_LINES) List<@Valid CartItem> items, String couponCode) {
    }

    public record QuoteLine(long variantId, long productId, String slug, String name, String brand, String image,
                            String size, BigDecimal unitPrice, BigDecimal originalPrice, int quantity, int stock,
                            boolean available, BigDecimal lineTotal, String message) {
    }

    public record Quote(List<QuoteLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal shippingFee,
                        BigDecimal total, String couponCode, boolean couponApplied, String couponMessage,
                        boolean valid) {
    }

    public record CustomerInfo(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @Email @Size(max = 160) String email,
            @NotBlank @Size(max = 255) String address,
            @NotBlank @Size(max = 80) String province,
            @Size(max = 500) String note) {
    }

    public record CardInfo(
            @NotBlank @Pattern(regexp = "^[0-9 ]{13,23}$", message = "Số thẻ không hợp lệ") String number,
            @NotBlank @Size(max = 60) String holder,
            @NotBlank @Pattern(regexp = "^(0[1-9]|1[0-2])/\\d{2}$", message = "Hạn thẻ dạng MM/YY") String expiry,
            @NotBlank @Pattern(regexp = "^\\d{3,4}$", message = "CVV gồm 3-4 chữ số") String cvv) {
    }

    public record PlaceOrderRequest(
            @NotEmpty @Size(max = MAX_LINES) List<@Valid CartItem> items,
            String couponCode,
            @NotNull @Valid CustomerInfo customer,
            @NotNull Order.PaymentMethod paymentMethod,
            @Valid CardInfo card) {
    }

    public record OrderItemDto(long productId, long variantId, String productName, String size, String image,
                               BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    public record HistoryDto(String fromStatus, String toStatus, String note, String changedBy, Instant changedAt) {
    }

    public record OrderDto(String code, String status, String paymentMethod, String paymentStatus, String paymentRef,
                           String paymentError, String customerName, String phone, String email, String address,
                           String province, String note, BigDecimal subtotal, BigDecimal discount,
                           BigDecimal shippingFee, BigDecimal total, String couponCode, Long userId,
                           Instant createdAt, Instant updatedAt, List<OrderItemDto> items, List<HistoryDto> history) {
    }

    public record OrderSummary(String code, String status, String paymentMethod, String paymentStatus,
                               BigDecimal total, int itemCount, String firstImage, Instant createdAt) {
    }

    // ------------------------------------------------------------------ cart

    public Quote quote(QuoteRequest req) {
        return tx.execute(s -> {
            List<CartItem> items = merge(req.items());
            Map<Long, ProductVariant> found = items.isEmpty() ? Map.of() : byId(variants.findAllWithProduct(ids(items)));
            return price(items, found, req.couponCode(), false);
        });
    }

    /**
     * Prices a cart. When {@code strict}, any problem (missing item, not enough stock, bad coupon) is an error;
     * otherwise problems are reported per line so the cart page can show them.
     */
    private Quote price(List<CartItem> items, Map<Long, ProductVariant> found, String couponCode, boolean strict) {
        List<QuoteLine> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean valid = !items.isEmpty();
        for (CartItem item : items) {
            ProductVariant v = found.get(item.variantId());
            if (v == null || !v.getProduct().isActive()) {
                if (strict) {
                    throw BusinessException.conflict("ITEM_UNAVAILABLE", "Một sản phẩm trong giỏ không còn được bán");
                }
                valid = false;
                lines.add(new QuoteLine(item.variantId(), 0, null, "Sản phẩm không còn bán", "", "", "", BigDecimal.ZERO,
                        BigDecimal.ZERO, item.quantity(), 0, false, BigDecimal.ZERO, "Sản phẩm không còn được bán"));
                continue;
            }
            var p = v.getProduct();
            boolean enough = v.getStock() >= item.quantity();
            String message = null;
            if (!enough) {
                message = v.getStock() == 0 ? "Size " + v.getSize() + " đã hết hàng"
                        : "Size " + v.getSize() + " chỉ còn " + v.getStock() + " đôi";
                if (strict) {
                    throw BusinessException.conflict("OUT_OF_STOCK", p.getName() + ": " + message);
                }
                valid = false;
            }
            BigDecimal unit = p.effectivePrice();
            BigDecimal line = unit.multiply(BigDecimal.valueOf(item.quantity()));
            subtotal = subtotal.add(line);
            lines.add(new QuoteLine(v.getId(), p.getId(), p.getSlug(), p.getName(), p.getBrand(), p.getImage(),
                    v.getSize(), unit, p.getPrice(), item.quantity(), v.getStock(), enough, line, message));
        }
        BigDecimal discount = BigDecimal.ZERO;
        String code = couponCode == null || couponCode.isBlank() ? null : couponCode.trim().toUpperCase();
        boolean applied = false;
        String couponMessage = null;
        if (code != null) {
            Coupon c = (strict ? coupons.lockByCode(code) : coupons.findByCode(code)).orElse(null);
            String reason = c == null ? "Mã giảm giá không tồn tại" : c.rejectReason(subtotal, LocalDate.now(clock.withZone(SHOP_ZONE)));
            if (reason == null) {
                discount = c.discountFor(subtotal);
                applied = true;
                couponMessage = c.getDescription();
            } else {
                if (strict) {
                    throw BusinessException.unprocessable("COUPON_INVALID", reason);
                }
                couponMessage = reason;
            }
        }
        BigDecimal afterDiscount = subtotal.subtract(discount);
        BigDecimal shipping = items.isEmpty() || afterDiscount.compareTo(props.shipping().freeFrom()) >= 0
                ? BigDecimal.ZERO : props.shipping().fee();
        return new Quote(lines, subtotal, discount, shipping, afterDiscount.add(shipping), code, applied, couponMessage,
                valid);
    }

    // ------------------------------------------------------------------ checkout

    public OrderDto place(PlaceOrderRequest req, CurrentUser user) {
        if (req.paymentMethod() == Order.PaymentMethod.CARD && req.card() == null) {
            throw BusinessException.badRequest("CARD_REQUIRED", "Vui lòng nhập thông tin thẻ");
        }
        String code = tx.execute(s -> reserve(req, user));
        Order.PaymentMethod method = req.paymentMethod();
        if (method == Order.PaymentMethod.CARD) {
            BigDecimal total = tx.execute(s -> load(code).getTotal());
            PaymentClient.ChargeResult result;
            PaymentClient.PiiFields pii = simulation.emitPiiActive() ? new PaymentClient.PiiFields(
                    SimulationService.PII_EMAIL, SimulationService.PII_CARD_NUMBER, SimulationService.PII_CARD_CVV) : null;
            if (pii != null) {
                // EMIT_PII fault injection: fixed fake values, never the customer's input.
                MDC.put("customer.email", pii.email());
                MDC.put("payment.card_number", pii.cardNumber());
                MDC.put("payment.card_cvv", pii.cardCvv());
                log.info("Card checkout order={} customer.email={} payment.card_number={} payment.card_cvv={}", code,
                        pii.email(), pii.cardNumber(), pii.cardCvv());
            }
            try {
                result = paymentClient.charge(code, total, new PaymentClient.Card(req.card().number().replace(" ", ""),
                        req.card().holder(), req.card().expiry(), req.card().cvv()), pii);
            } catch (RuntimeException e) {
                // Whatever went wrong talking to the gateway, the reservation must be released.
                tx.executeWithoutResult(s -> paymentFailed(code, "Cổng thanh toán không phản hồi"));
                meters.counter("shop.payments", "result", "gateway_error").increment();
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                        "Cổng thanh toán đang gặp sự cố nên đơn hàng chưa được thanh toán. "
                                + "Vui lòng thử lại sau hoặc chọn thanh toán khi nhận hàng.");
            }
            if (!result.approved()) {
                tx.executeWithoutResult(s -> paymentFailed(code, result.reason()));
                meters.counter("shop.payments", "result", result.businessError() ? "business_error" : "declined").increment();
                throw BusinessException.unprocessable("PAYMENT_DECLINED", result.businessError()
                        ? "Thanh toán không thành công " + result.reason()
                        : "Thanh toán bị từ chối: " + result.reason());
            }
            tx.executeWithoutResult(s -> paid(code, result.reference()));
            meters.counter("shop.payments", "result", "approved").increment();
        }
        OrderDto dto = tx.execute(s -> toDto(load(code)));
        orderValue.record(dto.total().doubleValue());
        Counter.builder("shop.orders.placed").tag("payment", method.name()).register(meters).increment();
        log.info("Order placed code={} total={} items={} payment={} coupon={} customer={}", dto.code(), dto.total(),
                dto.items().stream().mapToInt(OrderItemDto::quantity).sum(), method, dto.couponCode(),
                user == null ? "guest" : "user:" + user.id());
        return dto;
    }

    private String reserve(PlaceOrderRequest req, CurrentUser user) {
        List<CartItem> items = merge(req.items());
        if (items.isEmpty()) {
            throw BusinessException.badRequest("CART_EMPTY", "Giỏ hàng đang trống");
        }
        Map<Long, ProductVariant> locked = byId(variants.lockAll(ids(items)));
        Quote q = price(items, locked, req.couponCode(), true);
        if (q.couponApplied()) {
            coupons.lockByCode(q.couponCode()).ifPresent(c -> c.setUsedCount(c.getUsedCount() + 1));
        }
        Instant now = clock.instant();
        Order o = new Order();
        o.setCode("SB" + orders.nextCode());
        o.setUserId(user == null ? null : user.id());
        CustomerInfo c = req.customer();
        o.setCustomerName(c.name().trim());
        o.setPhone(c.phone());
        o.setEmail(blankToNull(c.email()));
        o.setAddress(c.address().trim());
        o.setProvince(c.province().trim());
        o.setNote(blankToNull(c.note()));
        o.setStatus(Order.Status.PENDING);
        o.setPaymentMethod(req.paymentMethod());
        o.setPaymentStatus(Order.PaymentStatus.UNPAID);
        o.setSubtotal(q.subtotal());
        o.setDiscount(q.discount());
        o.setShippingFee(q.shippingFee());
        o.setTotal(q.total());
        o.setCouponCode(q.couponApplied() ? q.couponCode() : null);
        o.setCreatedAt(now);
        o.setUpdatedAt(now);
        for (QuoteLine line : q.lines()) {
            ProductVariant v = locked.get(line.variantId());
            v.setStock(v.getStock() - line.quantity());
            v.getProduct().setSoldCount(v.getProduct().getSoldCount() + line.quantity());
            OrderItem item = new OrderItem();
            item.setOrder(o);
            item.setProductId(line.productId());
            item.setVariantId(line.variantId());
            item.setProductName(line.name());
            item.setSize(line.size());
            item.setImage(line.image());
            item.setUnitPrice(line.unitPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(line.lineTotal());
            o.getItems().add(item);
        }
        addHistory(o, null, Order.Status.PENDING, "Đặt hàng", user == null ? "Khách vãng lai" : user.email());
        orders.save(o);
        return o.getCode();
    }

    private void paid(String code, String reference) {
        Order o = load(code);
        o.setPaymentStatus(Order.PaymentStatus.PAID);
        o.setPaymentRef(reference);
        o.setUpdatedAt(clock.instant());
        addHistory(o, o.getStatus(), o.getStatus(), "Thanh toán thẻ thành công (" + reference + ")", "Cổng thanh toán");
    }

    private void paymentFailed(String code, String reason) {
        Order o = load(code);
        Order.Status from = o.getStatus();
        o.setStatus(Order.Status.PAYMENT_FAILED);
        o.setPaymentStatus(Order.PaymentStatus.FAILED);
        o.setPaymentError(reason == null ? null : reason.substring(0, Math.min(reason.length(), 255)));
        o.setUpdatedAt(clock.instant());
        releaseReservation(o);
        addHistory(o, from, Order.Status.PAYMENT_FAILED, "Thanh toán thất bại: " + reason, "Cổng thanh toán");
        log.warn("Payment failed for order {}: {}", code, reason);
    }

    /** Gives the stock, sales count and coupon use back. */
    private void releaseReservation(Order o) {
        Map<Long, ProductVariant> locked = byId(variants.lockAll(o.getItems().stream().map(OrderItem::getVariantId)
                .collect(Collectors.toSet())));
        for (OrderItem item : o.getItems()) {
            ProductVariant v = locked.get(item.getVariantId());
            v.setStock(v.getStock() + item.getQuantity());
            v.getProduct().setSoldCount(Math.max(0, v.getProduct().getSoldCount() - item.getQuantity()));
        }
        if (o.getCouponCode() != null) {
            coupons.lockByCode(o.getCouponCode()).ifPresent(c -> c.setUsedCount(Math.max(0, c.getUsedCount() - 1)));
        }
    }

    // ------------------------------------------------------------------ life cycle

    private static final Map<Order.Status, Set<Order.Status>> TRANSITIONS = Map.of(
            Order.Status.PENDING, EnumSet.of(Order.Status.CONFIRMED, Order.Status.CANCELLED),
            Order.Status.CONFIRMED, EnumSet.of(Order.Status.SHIPPING, Order.Status.CANCELLED),
            Order.Status.SHIPPING, EnumSet.of(Order.Status.DELIVERED),
            Order.Status.DELIVERED, EnumSet.noneOf(Order.Status.class),
            Order.Status.CANCELLED, EnumSet.noneOf(Order.Status.class),
            Order.Status.PAYMENT_FAILED, EnumSet.noneOf(Order.Status.class));

    public static Set<Order.Status> nextStatuses(Order.Status from) {
        return TRANSITIONS.get(from);
    }

    /** Admin status change. */
    public OrderDto changeStatus(String code, Order.Status target, String note, String by) {
        if (target == Order.Status.CANCELLED) {
            return cancel(code, note, by, false);
        }
        return tx.execute(s -> {
            Order o = load(code);
            Order.Status from = o.getStatus();
            if (!TRANSITIONS.get(from).contains(target)) {
                throw BusinessException.conflict("INVALID_TRANSITION", "Không thể chuyển đơn từ \""
                        + label(from) + "\" sang \"" + label(target) + "\"");
            }
            if (target == Order.Status.CONFIRMED && o.getPaymentMethod() == Order.PaymentMethod.CARD
                    && o.getPaymentStatus() != Order.PaymentStatus.PAID) {
                throw BusinessException.conflict("NOT_PAID", "Đơn thanh toán thẻ chưa được thanh toán");
            }
            o.setStatus(target);
            if (target == Order.Status.DELIVERED && o.getPaymentMethod() == Order.PaymentMethod.COD) {
                o.setPaymentStatus(Order.PaymentStatus.PAID);
            }
            o.setUpdatedAt(clock.instant());
            addHistory(o, from, target, blankToNull(note), by);
            log.info("Order {} {} -> {} by {}", code, from, target, by);
            return toDto(o);
        });
    }

    /** Customer cancellation: own order, still pending. */
    public OrderDto cancelByCustomer(String code, CurrentUser user) {
        tx.executeWithoutResult(s -> {
            Order o = load(code);
            if (o.getUserId() == null || o.getUserId() != user.id()) {
                throw BusinessException.notFound("đơn hàng");
            }
            if (o.getStatus() != Order.Status.PENDING) {
                throw BusinessException.conflict("CANNOT_CANCEL", "Chỉ hủy được đơn đang chờ xác nhận");
            }
        });
        return cancel(code, "Khách hàng hủy đơn", user.email(), true);
    }

    private OrderDto cancel(String code, String note, String by, boolean byCustomer) {
        Order snapshot = tx.execute(s -> {
            Order o = load(code);
            if (!TRANSITIONS.get(o.getStatus()).contains(Order.Status.CANCELLED)) {
                throw BusinessException.conflict("CANNOT_CANCEL", "Không thể hủy đơn ở trạng thái \"" + label(o.getStatus()) + "\"");
            }
            return o;
        });
        boolean refund = snapshot.getPaymentStatus() == Order.PaymentStatus.PAID && snapshot.getPaymentRef() != null;
        if (refund) {
            try {
                paymentClient.refund(snapshot.getPaymentRef(), snapshot.getTotal());
            } catch (PaymentClient.GatewayException e) {
                throw new BusinessException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                        "Không hoàn tiền được qua cổng thanh toán, đơn hàng chưa bị hủy. Vui lòng thử lại sau.");
            }
        }
        return tx.execute(s -> {
            Order o = load(code);
            Order.Status from = o.getStatus();
            if (!TRANSITIONS.get(from).contains(Order.Status.CANCELLED)) {
                throw BusinessException.conflict("CANNOT_CANCEL", "Đơn hàng vừa được cập nhật, không thể hủy");
            }
            o.setStatus(Order.Status.CANCELLED);
            if (refund) {
                o.setPaymentStatus(Order.PaymentStatus.REFUNDED);
            }
            o.setUpdatedAt(clock.instant());
            releaseReservation(o);
            addHistory(o, from, Order.Status.CANCELLED, blankToNull(note) == null ? "Hủy đơn" : note, by);
            log.info("Order {} cancelled by {}{}", code, byCustomer ? "customer " : "", by);
            return toDto(o);
        });
    }

    // ------------------------------------------------------------------ queries

    public List<OrderSummary> myOrders(CurrentUser user) {
        return tx.execute(s -> orders.findByUserIdOrderByCreatedAtDesc(user.id()).stream().map(OrderService::summary).toList());
    }

    public OrderDto myOrder(String code, CurrentUser user) {
        return tx.execute(s -> {
            Order o = load(code);
            if (o.getUserId() == null || o.getUserId() != user.id()) {
                throw BusinessException.notFound("đơn hàng");
            }
            return toDto(o);
        });
    }

    /** Guest order tracking: the phone number must match. */
    public OrderDto lookup(String code, String phone) {
        return tx.execute(s -> orders.findByCode(code.trim().toUpperCase())
                .filter(o -> o.getPhone().equals(phone == null ? "" : phone.trim()))
                .map(this::toDto)
                .orElseThrow(() -> BusinessException.notFound("đơn hàng với mã và số điện thoại này")));
    }

    public OrderDto adminGet(String code) {
        return tx.execute(s -> toDto(load(code)));
    }

    // ------------------------------------------------------------------ helpers

    private Order load(String code) {
        return orders.findByCode(code).orElseThrow(() -> BusinessException.notFound("đơn hàng"));
    }

    private void addHistory(Order o, Order.Status from, Order.Status to, String note, String by) {
        OrderHistory h = new OrderHistory();
        h.setOrder(o);
        h.setFromStatus(from == null ? null : from.name());
        h.setToStatus(to.name());
        h.setNote(note);
        h.setChangedBy(by);
        h.setChangedAt(clock.instant());
        o.getHistory().add(h);
    }

    private OrderDto toDto(Order o) {
        return new OrderDto(o.getCode(), o.getStatus().name(), o.getPaymentMethod().name(), o.getPaymentStatus().name(),
                o.getPaymentRef(), o.getPaymentError(), o.getCustomerName(), o.getPhone(), o.getEmail(), o.getAddress(),
                o.getProvince(), o.getNote(), o.getSubtotal(), o.getDiscount(), o.getShippingFee(), o.getTotal(),
                o.getCouponCode(), o.getUserId(), o.getCreatedAt(), o.getUpdatedAt(),
                o.getItems().stream().map(i -> new OrderItemDto(i.getProductId(), i.getVariantId(), i.getProductName(),
                        i.getSize(), i.getImage(), i.getUnitPrice(), i.getQuantity(), i.getLineTotal())).toList(),
                o.getHistory().stream().map(h -> new HistoryDto(h.getFromStatus(), h.getToStatus(), h.getNote(),
                        h.getChangedBy(), h.getChangedAt())).toList());
    }

    private static OrderSummary summary(Order o) {
        return new OrderSummary(o.getCode(), o.getStatus().name(), o.getPaymentMethod().name(),
                o.getPaymentStatus().name(), o.getTotal(), o.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                o.getItems().isEmpty() ? null : o.getItems().get(0).getImage(), o.getCreatedAt());
    }

    /** Same variant twice in a cart becomes one line (quantities summed, capped). */
    private static List<CartItem> merge(List<CartItem> items) {
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (CartItem i : items == null ? List.<CartItem>of() : items) {
            if (i != null && i.variantId() != null && i.quantity() > 0) {
                qty.merge(i.variantId(), i.quantity(), Integer::sum);
            }
        }
        if (qty.size() > MAX_LINES) {
            throw BusinessException.badRequest("CART_TOO_LARGE", "Giỏ hàng tối đa " + MAX_LINES + " sản phẩm");
        }
        return qty.entrySet().stream().map(e -> new CartItem(e.getKey(), Math.min(e.getValue(), MAX_QTY))).toList();
    }

    private static Set<Long> ids(List<CartItem> items) {
        return items.stream().map(CartItem::variantId).collect(Collectors.toSet());
    }

    private static Map<Long, ProductVariant> byId(List<ProductVariant> list) {
        return list.stream().collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
    }

    static String label(Order.Status s) {
        return switch (s) {
            case PENDING -> "Chờ xác nhận";
            case PAYMENT_FAILED -> "Thanh toán thất bại";
            case CONFIRMED -> "Đã xác nhận";
            case SHIPPING -> "Đang giao";
            case DELIVERED -> "Đã giao";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
