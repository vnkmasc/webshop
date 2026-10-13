package vn.saibuoc.payment;

import jakarta.validation.Valid;
import java.math.BigDecimal;
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

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    public record RefundRequest(BigDecimal amount) {
    }

    @PostMapping("/api/payments/charge")
    public Envelope<PaymentService.ChargeResult> charge(@RequestBody PaymentService.ChargeRequest req) {
        if (req.orderCode() == null || req.amount() == null || req.amount().signum() <= 0 || req.card() == null) {
            throw new IllegalArgumentException("orderCode, amount and card are required");
        }
        return Envelope.ok(payments.charge(req));
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
