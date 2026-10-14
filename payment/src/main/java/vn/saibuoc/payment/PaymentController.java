package vn.saibuoc.payment;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    static final String RESPONSE_CODE_HEADER = "X-Response-Code";

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record RefundRequest(BigDecimal amount) {
    }

    @PostMapping("/api/payments/charge")
    public ResponseEntity<Map<String, Object>> charge(@RequestBody PaymentService.ChargeRequest req) {
        if (req.orderCode() == null || req.amount() == null || req.amount().signum() <= 0 || req.card() == null) {
            throw new IllegalArgumentException("orderCode, amount and card are required");
        }
        PaymentService.ChargeResult result = payments.charge(req);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("responseCode", "00");
        body.put("responseMessage", "Giao dịch được xử lý");
        body.put("data", result);
        return ResponseEntity.ok().header(RESPONSE_CODE_HEADER, "00").body(body);
    }

    /** Core-banking style failure: HTTP 200, the outcome is only in the body and the X-Response-Code header. */
    @ExceptionHandler(PaymentService.BusinessCodeException.class)
    public ResponseEntity<Map<String, Object>> businessCode(PaymentService.BusinessCodeException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("responseCode", e.code());
        body.put("responseMessage", e.getMessage());
        body.put("data", null);
        return ResponseEntity.ok().header(RESPONSE_CODE_HEADER, e.code()).body(body);
    }

    public record BusinessErrorRequest(String code, int percent, java.time.Instant until) {
    }

    @GetMapping("/api/admin/business-error")
    public Envelope<BusinessErrorMode> businessError() {
        return Envelope.ok(payments.businessError());
    }

    @PutMapping("/api/admin/business-error")
    public Envelope<BusinessErrorMode> setBusinessError(@RequestBody BusinessErrorRequest req) {
        return Envelope.ok(payments.setBusinessError(new BusinessErrorMode(req.code(), req.percent(), req.until())));
    }

    @Transactional
    @PostMapping("/api/payments/{reference}/refund")
    public Envelope<PaymentService.ChargeResult> refund(@PathVariable String reference, @RequestBody RefundRequest req) {
        return Envelope.ok(payments.refund(reference, req.amount()));
    }

    @GetMapping("/api/admin/settings")
    public Envelope<Map<String, Object>> settings() {
        return Envelope.ok(view(payments.settings()));
    }

    @PutMapping("/api/admin/settings")
    public Envelope<Map<String, Object>> update(@Valid @RequestBody GatewaySettings settings) {
        return Envelope.ok(view(payments.update(settings)));
    }

    private Map<String, Object> view(GatewaySettings s) {
        return Map.of("baseLatencyMs", s.baseLatencyMs(), "extraLatencyMs", s.extraLatencyMs(),
                "errorRatePercent", s.errorRatePercent(), "declineRatePercent", s.declineRatePercent(),
                "outage", s.outage(), "lastHour", payments.stats());
    }

    @ExceptionHandler(PaymentService.AcquirerException.class)
    public ResponseEntity<Envelope<Void>> acquirer(PaymentService.AcquirerException e) {
        return ResponseEntity.status(e.status()).body(Envelope.error("ACQUIRER_ERROR", e.getMessage()));
    }

    @ExceptionHandler(PaymentService.NotFoundException.class)
    public ResponseEntity<Envelope<Void>> notFound(PaymentService.NotFoundException e) {
        return ResponseEntity.status(404).body(Envelope.error("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Envelope<Void>> conflict(IllegalStateException e) {
        return ResponseEntity.status(409).body(Envelope.error("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Envelope<Void>> badRequest(Exception e) {
        return ResponseEntity.badRequest().body(Envelope.error("BAD_REQUEST", "Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Envelope<Void>> unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(500).body(Envelope.error("INTERNAL_ERROR", "Internal error"));
    }
}
