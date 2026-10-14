package vn.saibuoc.shop.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

/** HTTP client for the card payment gateway service. Card data is passed through, never stored or logged. */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentClient {

    private final RestClient paymentRestClient;

    public record Card(String number, String holder, String expiry, String cvv) {
    }

    /** status: APPROVED, DECLINED, or BUSINESS_ERROR (HTTP 200 with a responseCode other than "00"). */
    public record ChargeResult(String reference, String status, String reason) {
        public boolean approved() {
            return "APPROVED".equals(status);
        }

        public boolean businessError() {
            return "BUSINESS_ERROR".equals(status);
        }
    }

    /** responseCode/responseMessage: core-banking style outcome carried in the body ("00" = processed). */
    record Envelope<T>(boolean success, T data, String message, String errorCode, String responseCode,
                       String responseMessage) {
    }

    public record BusinessErrorMode(String code, int percent, Instant until) {
    }

    /** Gateway unreachable, timed out or answered with a server error. */
    public static class GatewayException extends RuntimeException {
        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public ChargeResult charge(String orderCode, BigDecimal amount, Card card) {
        Map<String, Object> body = Map.of("orderCode", orderCode, "amount", amount, "currency", "VND",
                "card", Map.of("number", card.number(), "holder", card.holder(), "expiry", card.expiry(), "cvv", card.cvv()));
        try {
            Envelope<ChargeResult> res = paymentRestClient.post().uri("/api/payments/charge")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", requestId())
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Envelope<ChargeResult>>() { });
            // HTTP 200 does not mean the payment went through: the outcome is the body's responseCode.
            if (res != null && res.responseCode() != null && !"00".equals(res.responseCode())) {
                log.warn("Payment for order {} rejected by the core: responseCode={} ({})", orderCode,
                        res.responseCode(), res.responseMessage());
                return new ChargeResult(null, "BUSINESS_ERROR", "[" + res.responseCode() + "] " + res.responseMessage());
            }
            if (res == null || res.data() == null) {
                throw new GatewayException("Empty response from payment gateway", null);
            }
            log.info("Payment for order {} amount={} -> {} ref={}", orderCode, amount, res.data().status(),
                    res.data().reference());
            return res.data();
        } catch (HttpStatusCodeException e) {
            log.error("Payment gateway returned HTTP {} for order {}", e.getStatusCode().value(), orderCode);
            throw new GatewayException("Payment gateway HTTP " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            // Connection refused, timeouts (also while reading the body) and unreadable responses.
            log.error("Payment gateway unreachable or timed out for order {}: {}", orderCode, e.getMessage());
            throw new GatewayException("Payment gateway unreachable: " + e.getMessage(), e);
        }
    }

    public void refund(String reference, BigDecimal amount) {
        try {
            paymentRestClient.post().uri("/api/payments/{ref}/refund", reference)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", requestId())
                    .body(Map.of("amount", amount))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Refunded payment {} amount={}", reference, amount);
        } catch (RestClientException e) {
            log.error("Refund of payment {} failed: {}", reference, e.getMessage());
            throw new GatewayException("Refund failed: " + e.getMessage(), e);
        }
    }

    /** Switches the gateway's business-error mode on (until the given instant) or off (percent 0). */
    public void setBusinessError(BusinessErrorMode mode) {
        try {
            paymentRestClient.put().uri("/api/admin/business-error").contentType(MediaType.APPLICATION_JSON)
                    .body(mode).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw new GatewayException("Payment gateway unreachable: " + e.getMessage(), e);
        }
    }

    /** Admin pass-through for the gateway's behaviour settings (latency, error and decline rates). */
    public Map<String, Object> settings() {
        try {
            Envelope<Map<String, Object>> res = paymentRestClient.get().uri("/api/admin/settings").retrieve()
                    .body(new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
            return res == null ? Map.of() : res.data();
        } catch (RestClientException e) {
            throw new GatewayException("Payment gateway unreachable: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> updateSettings(Map<String, Object> settings) {
        try {
            Envelope<Map<String, Object>> res = paymentRestClient.put().uri("/api/admin/settings")
                    .contentType(MediaType.APPLICATION_JSON).body(settings).retrieve()
                    .body(new ParameterizedTypeReference<Envelope<Map<String, Object>>>() { });
            return res == null ? Map.of() : res.data();
        } catch (RestClientException e) {
            throw new GatewayException("Payment gateway unreachable: " + e.getMessage(), e);
        }
    }

    private static String requestId() {
        String rid = MDC.get("requestId");
        return rid == null ? "" : rid;
    }
}
