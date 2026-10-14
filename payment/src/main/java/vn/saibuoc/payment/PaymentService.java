package vn.saibuoc.payment;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Simulated card acquirer: validates the card, "talks to the bank" and records every attempt. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final MeterRegistry meters;
    private final AtomicReference<GatewaySettings> settings = new AtomicReference<>(GatewaySettings.defaults());
    private final AtomicReference<BusinessErrorMode> businessError = new AtomicReference<>(null);

    public PaymentService(JdbcTemplate jdbc, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.meters = meters;
        this.clock = Clock.systemUTC();
    }

    public record Card(String number, String holder, String expiry, String cvv) {
    }

    public record ChargeRequest(String orderCode, BigDecimal amount, String currency, Card card) {
    }

    public record ChargeResult(String reference, String status, String reason) {
    }

    /** Thrown for simulated acquirer failures; answered with HTTP 502/503. */
    public static class AcquirerException extends RuntimeException {
        private final int status;

        public AcquirerException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** A charge answered with a business response code other than "00" (HTTP stays 200). */
    public static class BusinessCodeException extends RuntimeException {
        private final String code;

        public BusinessCodeException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) {
            super(message);
        }
    }

    public GatewaySettings settings() {
        return settings.get();
    }

    public GatewaySettings update(GatewaySettings s) {
        log.warn("Gateway settings changed: {}", s);
        settings.set(s);
        return s;
    }

    /** Current business-error mode, or null when off (expired modes are cleared here). */
    public BusinessErrorMode businessError() {
        BusinessErrorMode m = businessError.get();
        if (m != null && !m.activeAt(clock.instant())) {
            businessError.compareAndSet(m, null);
            log.warn("Business error mode {} expired", m.code());
            return null;
        }
        return m;
    }

    public BusinessErrorMode setBusinessError(BusinessErrorMode mode) {
        if (mode == null || mode.percent() <= 0) {
            businessError.set(null);
            log.warn("Business error mode switched off");
            return null;
        }
        if (!BusinessErrorMode.MESSAGES.containsKey(mode.code())) {
            throw new IllegalArgumentException("Unsupported response code " + mode.code());
        }
        businessError.set(new BusinessErrorMode(mode.code(), Math.min(mode.percent(), 100), mode.until()));
        log.warn("Business error mode on: code={} percent={} until={}", mode.code(), mode.percent(), mode.until());
        return businessError.get();
    }

    public ChargeResult charge(ChargeRequest req) {
        GatewaySettings s = settings.get();
        if (s.outage()) {
            meters.counter("gateway.charges", "result", "unavailable").increment();
            throw new AcquirerException(503, "Acquirer unavailable (maintenance window)");
        }
        long start = System.nanoTime();
        String number = req.card() == null || req.card().number() == null ? "" : req.card().number().replaceAll("\\D", "");
        String last4 = number.length() >= 4 ? number.substring(number.length() - 4) : "0000";
        String brand = brand(number);

        String reason = validate(number, req.card());
        authorizeWithBank(s);
        if (s.errorRatePercent() > 0 && ThreadLocalRandom.current().nextInt(100) < s.errorRatePercent()) {
            meters.counter("gateway.charges", "result", "error").increment();
            log.error("Acquirer connection reset while authorizing order {}", req.orderCode());
            throw new AcquirerException(502, "Acquirer connection reset by peer");
        }
        BusinessErrorMode mode = businessError();
        if (mode != null && ThreadLocalRandom.current().nextInt(100) < mode.percent()) {
            String msg = mode.message();
            jdbc.update("""
                    insert into payments (reference, order_code, amount, currency, card_brand, card_last4, status, reason,
                                          processing_ms, created_at)
                    values (?, ?, ?, ?, ?, ?, 'DECLINED', ?, ?, ?)""",
                    "PAY-" + HexFormat.of().withUpperCase().formatHex(randomBytes()), req.orderCode(), req.amount(),
                    req.currency() == null ? "VND" : req.currency(), brand, last4, "[" + mode.code() + "] " + msg,
                    (int) ((System.nanoTime() - start) / 1_000_000), Timestamp.from(clock.instant()));
            meters.counter("gateway.charges", "result", "business_error").increment();
            log.warn("Core banking responded code {} ({}) on POST /api/payments/charge order={}", mode.code(), msg,
                    req.orderCode());
            throw new BusinessCodeException(mode.code(), msg);
        }
        if (reason == null && number.endsWith("0002")) {
            reason = "Thẻ bị ngân hàng phát hành từ chối";
        }
        if (reason == null && number.endsWith("9995")) {
            reason = "Số dư tài khoản không đủ";
        }
        if (reason == null && s.declineRatePercent() > 0
                && ThreadLocalRandom.current().nextInt(100) < s.declineRatePercent()) {
            reason = "Giao dịch bị từ chối do nghi ngờ gian lận";
        }
        String status = reason == null ? "APPROVED" : "DECLINED";
        String reference = "PAY-" + HexFormat.of().withUpperCase().formatHex(randomBytes());
        int ms = (int) ((System.nanoTime() - start) / 1_000_000);
        jdbc.update("""
                insert into payments (reference, order_code, amount, currency, card_brand, card_last4, status, reason,
                                      processing_ms, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", reference, req.orderCode(), req.amount(),
                req.currency() == null ? "VND" : req.currency(), brand, last4, status, reason, ms,
                Timestamp.from(clock.instant()));
        meters.counter("gateway.charges", "result", status.toLowerCase()).increment();
        Timer.builder("gateway.charge.duration").register(meters).record(java.time.Duration.ofMillis(ms));
        log.info("Charge order={} amount={} brand={} last4={} -> {}{} ({} ms)", req.orderCode(), req.amount(), brand,
                last4, status, reason == null ? "" : " (" + reason + ")", ms);
        return new ChargeResult(reference, status, reason);
    }

    public ChargeResult refund(String reference, BigDecimal amount) {
        if (settings.get().outage()) {
            throw new AcquirerException(503, "Acquirer unavailable (maintenance window)");
        }
        authorizeWithBank(settings.get());
        List<Map<String, Object>> rows = jdbc.queryForList("select status, amount from payments where reference = ? for update",
                reference);
        if (rows.isEmpty()) {
            throw new NotFoundException("Payment " + reference + " not found");
        }
        String status = (String) rows.get(0).get("status");
        if ("REFUNDED".equals(status)) {
            return new ChargeResult(reference, "REFUNDED", null);
        }
        if (!"APPROVED".equals(status)) {
            throw new IllegalStateException("Payment " + reference + " is " + status + " and cannot be refunded");
        }
        jdbc.update("update payments set status = 'REFUNDED', refunded_at = ? where reference = ?",
                Timestamp.from(clock.instant()), reference);
        meters.counter("gateway.refunds").increment();
        log.info("Refund {} amount={}", reference, amount);
        return new ChargeResult(reference, "REFUNDED", null);
    }

    public Map<String, Object> stats() {
        return jdbc.queryForMap("""
                select count(*) filter (where status in ('APPROVED', 'REFUNDED')) as approved,
                       count(*) filter (where status = 'DECLINED') as declined,
                       COALESCE(avg(processing_ms), 0)::int as avg_ms
                from payments where created_at > now() - interval '1 hour'""");
    }

    /** Round trip to the (imaginary) issuing bank. */
    private static void authorizeWithBank(GatewaySettings s) {
        int jitter = ThreadLocalRandom.current().nextInt(0, 150);
        sleep(s.baseLatencyMs() + jitter + s.extraLatencyMs());
    }

    private String validate(String number, Card card) {
        if (number.length() < 13 || number.length() > 19 || !luhn(number)) {
            return "Số thẻ không hợp lệ";
        }
        if (card.cvv() == null || !card.cvv().matches("\\d{3,4}")) {
            return "Mã CVV không hợp lệ";
        }
        try {
            String[] parts = card.expiry().split("/");
            YearMonth exp = YearMonth.of(2000 + Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            if (exp.isBefore(YearMonth.now(clock))) {
                return "Thẻ đã hết hạn";
            }
        } catch (RuntimeException e) {
            return "Hạn thẻ không hợp lệ";
        }
        return null;
    }

    static boolean luhn(String number) {
        int sum = 0;
        boolean alt = false;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = number.charAt(i) - '0';
            if (alt) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alt = !alt;
        }
        return sum % 10 == 0;
    }

    static String brand(String number) {
        if (number.startsWith("4")) {
            return "VISA";
        }
        if (number.matches("^5[1-5].*") || number.matches("^2[2-7].*")) {
            return "MASTERCARD";
        }
        if (number.startsWith("35")) {
            return "JCB";
        }
        if (number.startsWith("9704")) {
            return "NAPAS";
        }
        return "OTHER";
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[6];
        ThreadLocalRandom.current().nextBytes(b);
        return b;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
