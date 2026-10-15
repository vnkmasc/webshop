package vn.saibuoc.shop.simulation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.config.AppProperties;
import vn.saibuoc.shop.payment.PaymentClient;
import vn.saibuoc.shop.risk.RiskClient;

/**
 * Controlled fault injection for test environments. Off unless APP_SIMULATION_ENABLED=true.
 * State lives in memory, every scenario switches itself off after its duration, and no scenario changes shop data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    public enum Scenario {
        /** Random extra latency on a group of endpoints. params: target, minMs, maxMs */
        LATENCY,
        /** A percentage of requests on a group of endpoints fail with a real exception. params: target, ratePercent */
        HTTP_ERROR,
        /** Product listing and search run an extra slow SQL statement. params: sleepMs */
        SLOW_SQL,
        /** Product listing loads the sizes of each product with its own query. */
        N_PLUS_ONE,
        /** Requests hold a DB connection for a while, starving the pool. params: target, holdMs, ratePercent */
        POOL_EXHAUSTION,
        /** Product page recommendations burn CPU. params: cpuMs */
        HIGH_CPU,
        /** Each request retains memory until released, bounded by a cap. params: kbPerRequest, capMb */
        MEMORY_LEAK,
        /** The storefront "add to cart" button throws a JavaScript error. */
        FRONTEND_ERROR,
        /**
         * The payment gateway answers HTTP 200 with a business responseCode other than "00" (core-banking style).
         * params: code (99 | 68 | 12), percent
         */
        PAYMENT_BUSINESS_ERROR,
        /** A share of requests answer with an uncommon HTTP status (423, 402, 451, 429). params: code, percent, target */
        ODD_STATUS,
        /** /actuator/health/readiness reports DOWN (liveness stays UP), so load balancers take the instance out. */
        READINESS_FAIL,
        /**
         * Card checkouts attach fake sensitive data (public test card, demo e-mail, CVV 000) to the gateway call
         * (query string and headers) and to the log, to demonstrate masking. Never real customer data.
         */
        EMIT_PII,
        /**
         * A counter gets a tag whose value is unique per request, exploding the number of metric series.
         * Hard cap MAX_TAGGED_SERIES; all series are removed when the scenario ends. params: perRequest, maxSeries
         */
        METRIC_CARDINALITY,
        /** The risk service answers N ms later (params: delayMs, default 2000); checkout waits for it. */
        RISK_SLOW,
        /** A share of risk scoring calls fail with 500 (params: percent, default 30); orders still go through. */
        RISK_ERROR,
        /** The risk service answers after the backend timeout (params: delayMs, default 5000; timeout 3 s). */
        RISK_TIMEOUT,
        /** The order queue consumer takes N ms longer per message (params: delayMs, default 5000, max 20000). */
        KAFKA_SLOW_CONSUMER,
        /** The order queue consumer stops (leaves its group) while orders keep being published. */
        KAFKA_CONSUMER_DEAD
    }

    private static final java.util.Set<Scenario> QUEUE_SCENARIOS = java.util.EnumSet.of(Scenario.KAFKA_SLOW_CONSUMER,
            Scenario.KAFKA_CONSUMER_DEAD);
    public static final int MAX_CONSUMER_DELAY_MS = 20000;

    private static final java.util.Set<Scenario> RISK_SCENARIOS = java.util.EnumSet.of(Scenario.RISK_SLOW,
            Scenario.RISK_ERROR, Scenario.RISK_TIMEOUT);

    public static final String TAGGED_METRIC = "shop.request.tagged";
    public static final int MAX_TAGGED_SERIES = 5000;

    /** Fixed fake values used by EMIT_PII. */
    public static final String PII_EMAIL = "demo@example.com";
    public static final String PII_CARD_NUMBER = "4111111111111111";
    public static final String PII_CARD_CVV = "000";

    /** Statuses ODD_STATUS can answer with: HTTP status -> {error, message}. */
    public static final Map<Integer, String[]> ODD_STATUSES = Map.of(
            423, new String[]{"ACCOUNT_LOCKED", "Tài khoản đang bị khoá do rà soát gian lận"},
            402, new String[]{"PAYMENT_REQUIRED", "Cần thanh toán trước khi tiếp tục"},
            451, new String[]{"LEGAL_HOLD", "Đơn hàng bị giữ theo yêu cầu pháp lý"},
            429, new String[]{"TOO_MANY_REQUESTS", "Quá nhiều yêu cầu, vui lòng thử lại sau"});

    /** Business response codes the gateway can return in PAYMENT_BUSINESS_ERROR. */
    public static final java.util.Set<String> BUSINESS_CODES = java.util.Set.of("99", "68", "12");

    /** Endpoint groups a scenario can target. */
    public enum Target {
        ALL("/api/"),
        CATALOG("/api/home", "/api/products", "/api/catalog"),
        CART("/api/cart"),
        CHECKOUT("/api/orders"),
        ACCOUNT("/api/auth", "/api/account"),
        ADMIN("/api/admin/");

        private final String[] prefixes;

        Target(String... prefixes) {
            this.prefixes = prefixes;
        }

        public boolean matches(String path) {
            for (String p : prefixes) {
                if (path.startsWith(p)) {
                    return true;
                }
            }
            return false;
        }
    }

    public record ScenarioState(Scenario scenario, boolean active, Map<String, Object> params, Instant expiresAt) {
    }

    private final AppProperties props;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final PaymentClient paymentClient;
    private final RiskClient riskClient;
    private final MeterRegistry meters;
    private final AtomicInteger taggedSeries = new AtomicInteger();

    private final Map<Scenario, ScenarioState> states = new EnumMap<>(Scenario.class);
    private final List<byte[]> retained = new ArrayList<>();

    public boolean enabled() {
        return props.simulationEnabled();
    }

    public synchronized List<ScenarioState> list() {
        List<ScenarioState> result = new ArrayList<>();
        for (Scenario s : Scenario.values()) {
            result.add(current(s));
        }
        return result;
    }

    public synchronized ScenarioState activate(Scenario scenario, Map<String, Object> params, int durationMinutes, String by) {
        int minutes = Math.min(Math.max(durationMinutes, 1), 240);
        ScenarioState state = new ScenarioState(scenario, true, Map.copyOf(params == null ? Map.of() : params),
                clock.instant().plus(Duration.ofMinutes(minutes)));
        if (scenario == Scenario.PAYMENT_BUSINESS_ERROR) {
            pushBusinessError(state);
        }
        if (scenario == Scenario.METRIC_CARDINALITY) {
            clearTaggedSeries();
        }
        if (RISK_SCENARIOS.contains(scenario)) {
            pushRiskScenario(scenario, true, state);
        }
        if (QUEUE_SCENARIOS.contains(scenario) && (props.events() == null || !props.events().enabled())) {
            throw BusinessException.badRequest("QUEUE_DISABLED", "Hàng đợi đơn hàng chưa được cấu hình (APP_KAFKA_BOOTSTRAP)");
        }
        if (scenario == Scenario.ODD_STATUS && !ODD_STATUSES.containsKey(intParam(state, "code", 423))) {
            throw BusinessException.badRequest("BAD_CODE", "Mã HTTP phải là một trong " + ODD_STATUSES.keySet());
        }
        states.put(scenario, state);
        log.warn("Simulation scenario {} activated by {} for {} min params={}", scenario, by, minutes, state.params());
        return state;
    }

    public synchronized ScenarioState deactivate(Scenario scenario, String by) {
        states.remove(scenario);
        if (scenario == Scenario.PAYMENT_BUSINESS_ERROR) {
            clearBusinessError();
        }
        if (scenario == Scenario.METRIC_CARDINALITY) {
            clearTaggedSeries();
        }
        if (scenario == Scenario.MEMORY_LEAK) {
            retained.clear();
        }
        if (RISK_SCENARIOS.contains(scenario)) {
            try {
                riskClient.setScenario(scenario.name(), false, null, null);
            } catch (RuntimeException e) {
                log.warn("Could not switch {} off in the risk service: {}", scenario, e.getMessage());
            }
        }
        log.warn("Simulation scenario {} deactivated by {}", scenario, by);
        return current(scenario);
    }

    public synchronized int releaseMemory() {
        int mb = retainedMb();
        retained.clear();
        return mb;
    }

    public synchronized int retainedMb() {
        return (int) (retained.stream().mapToLong(b -> b.length).sum() / (1024 * 1024));
    }

    private synchronized ScenarioState current(Scenario s) {
        ScenarioState st = states.get(s);
        if (st != null && st.expiresAt().isBefore(clock.instant())) {
            states.remove(s);
            if (s == Scenario.MEMORY_LEAK) {
                retained.clear();
            }
            if (s == Scenario.METRIC_CARDINALITY) {
                clearTaggedSeries();
            }
            log.warn("Simulation scenario {} expired", s);
            st = null;
        }
        return st == null ? new ScenarioState(s, false, Map.of(), null) : st;
    }

    private ScenarioState active(Scenario s) {
        if (!enabled()) {
            return null;
        }
        ScenarioState st = current(s);
        return st.active() ? st : null;
    }

    // ------------------------------------------------------------------ metric cardinality

    public int taggedSeries() {
        return taggedSeries.get();
    }

    private void addTaggedSeries(ScenarioState st) {
        int perRequest = Math.min(Math.max(intParam(st, "perRequest", 10), 1), 50);
        int cap = Math.min(Math.max(intParam(st, "maxSeries", MAX_TAGGED_SERIES), 1), MAX_TAGGED_SERIES);
        for (int i = 0; i < perRequest; i++) {
            if (taggedSeries.incrementAndGet() > cap) {
                taggedSeries.decrementAndGet();
                return;
            }
            Counter.builder(TAGGED_METRIC).description("Requests tagged with a unique reference (cardinality demo)")
                    .tag("request_ref", UUID.randomUUID().toString()).register(meters).increment();
        }
    }

    private void clearTaggedSeries() {
        int n = 0;
        for (Meter m : meters.find(TAGGED_METRIC).meters()) {
            meters.remove(m);
            n++;
        }
        taggedSeries.set(0);
        if (n > 0) {
            log.warn("Removed {} series of {}", n, TAGGED_METRIC);
        }
    }

    // ------------------------------------------------------------------ payment gateway business-error mode

    /** The gateway keeps its own expiry (same instant), so it also switches off if this process is gone. */
    private void pushBusinessError(ScenarioState st) {
        String code = String.valueOf(st.params().getOrDefault("code", "99"));
        if (!BUSINESS_CODES.contains(code)) {
            throw BusinessException.badRequest("BAD_CODE", "Mã phản hồi phải là 99, 68 hoặc 12");
        }
        try {
            paymentClient.setBusinessError(new PaymentClient.BusinessErrorMode(code,
                    Math.min(Math.max(intParam(st, "percent", 40), 0), 100), st.expiresAt()));
        } catch (PaymentClient.GatewayException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "GATEWAY_UNREACHABLE",
                    "Không kết nối được cổng thanh toán để bật kịch bản");
        }
    }

    private void clearBusinessError() {
        try {
            paymentClient.setBusinessError(new PaymentClient.BusinessErrorMode("99", 0, null));
        } catch (PaymentClient.GatewayException e) {
            log.warn("Could not switch the gateway business-error mode off: {}", e.getMessage());
        }
    }

    private void pushRiskScenario(Scenario scenario, boolean active, ScenarioState st) {
        try {
            riskClient.setScenario(scenario.name(), active, st.params(), st.expiresAt());
        } catch (RuntimeException e) {
            throw new BusinessException(HttpStatus.BAD_GATEWAY, "RISK_UNREACHABLE",
                    "Không kết nối được dịch vụ chấm điểm rủi ro để bật kịch bản");
        }
    }

    /** Scenarios live in memory: after a restart nothing is active, so the gateway and risk service are reset too. */
    @EventListener(ApplicationReadyEvent.class)
    public void resetOnStartup() {
        if (enabled()) {
            clearBusinessError();
            if (riskClient.enabled()) {
                for (Scenario s : RISK_SCENARIOS) {
                    try {
                        riskClient.setScenario(s.name(), false, null, null);
                    } catch (RuntimeException e) {
                        log.warn("Could not reset {} in the risk service: {}", s, e.getMessage());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ hooks on the request path

    /** Applied by {@link SimulationInterceptor} before each API request. */
    public void beforeRequest(String path) {
        if (!enabled() || path.startsWith("/api/admin/simulation") || path.equals("/api/site-config")) {
            return;
        }
        ScenarioState latency = active(Scenario.LATENCY);
        if (latency != null && target(latency).matches(path)) {
            int min = intParam(latency, "minMs", 500);
            int max = Math.max(min, intParam(latency, "maxMs", 2000));
            sleep(ThreadLocalRandom.current().nextInt(min, max + 1));
        }
        ScenarioState pool = active(Scenario.POOL_EXHAUSTION);
        if (pool != null && target(pool).matches(path) && hit(intParam(pool, "ratePercent", 50))) {
            jdbc.queryForObject("select pg_sleep(?)", Object.class, intParam(pool, "holdMs", 5000) / 1000.0);
        }
        ScenarioState cardinality = active(Scenario.METRIC_CARDINALITY);
        if (cardinality != null) {
            addTaggedSeries(cardinality);
        }
        ScenarioState memory = active(Scenario.MEMORY_LEAK);
        if (memory != null) {
            retain(intParam(memory, "kbPerRequest", 512), intParam(memory, "capMb", 256));
        }
        ScenarioState odd = active(Scenario.ODD_STATUS);
        if (odd != null && target(odd).matches(path) && hit(intParam(odd, "percent", 40))) {
            int code = intParam(odd, "code", 423);
            String[] em = ODD_STATUSES.getOrDefault(code, ODD_STATUSES.get(423));
            throw new OddStatusException(ODD_STATUSES.containsKey(code) ? code : 423, em[0], em[1]);
        }
        ScenarioState error = active(Scenario.HTTP_ERROR);
        if (error != null && target(error).matches(path) && hit(intParam(error, "ratePercent", 20))) {
            throw new SimulatedFailureException("Simulated failure while processing " + path);
        }
    }

    public void slowQueryIfActive() {
        ScenarioState st = active(Scenario.SLOW_SQL);
        if (st != null) {
            jdbc.queryForObject("select count(*) from products p, pg_sleep(?) s where p.active",
                    Long.class, intParam(st, "sleepMs", 3000) / 1000.0);
        }
    }

    public boolean nPlusOneActive() {
        return active(Scenario.N_PLUS_ONE) != null;
    }

    public void burnCpuIfActive() {
        ScenarioState st = active(Scenario.HIGH_CPU);
        if (st == null) {
            return;
        }
        long until = System.nanoTime() + Duration.ofMillis(intParam(st, "cpuMs", 1500)).toNanos();
        double x = 0;
        while (System.nanoTime() < until) {
            for (int i = 1; i < 10_000; i++) {
                x += Math.sqrt(i) * Math.sin(x + i);
            }
        }
        if (x == 42) {
            log.trace("unreachable {}", x);
        }
    }

    public boolean emitPiiActive() {
        return active(Scenario.EMIT_PII) != null;
    }

    /** Extra processing time per queue message (KAFKA_SLOW_CONSUMER), 0 when off. */
    public long slowConsumerDelayMs() {
        ScenarioState st = active(Scenario.KAFKA_SLOW_CONSUMER);
        return st == null ? 0 : Math.min(Math.max(intParam(st, "delayMs", 5000), 0), MAX_CONSUMER_DELAY_MS);
    }

    public boolean consumerDeadActive() {
        return active(Scenario.KAFKA_CONSUMER_DEAD) != null;
    }

    public boolean readinessFailActive() {
        return active(Scenario.READINESS_FAIL) != null;
    }

    public boolean frontendErrorActive() {
        return active(Scenario.FRONTEND_ERROR) != null;
    }

    private synchronized void retain(int kb, int capMb) {
        if (retainedMb() < capMb) {
            retained.add(new byte[kb * 1024]);
        }
    }

    private static Target target(ScenarioState st) {
        Object t = st.params().get("target");
        try {
            return t == null ? Target.ALL : Target.valueOf(t.toString());
        } catch (IllegalArgumentException e) {
            return Target.ALL;
        }
    }

    private static int intParam(ScenarioState st, String key, int def) {
        Object v = st.params().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return v == null ? def : Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean hit(int percent) {
        return ThreadLocalRandom.current().nextInt(100) < Math.min(Math.max(percent, 0), 100);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thrown on purpose by ODD_STATUS; answered with that status, a JSON error body and Retry-After for 423/429. */
    @lombok.Getter
    public static class OddStatusException extends RuntimeException {
        private final int status;
        private final String error;

        public OddStatusException(int status, String error, String message) {
            super(message);
            this.status = status;
            this.error = error;
        }
    }

    /** Thrown on purpose by HTTP_ERROR; handled as an ordinary unexpected server error (500). */
    public static class SimulatedFailureException extends RuntimeException {
        public SimulatedFailureException(String message) {
            super(message);
        }
    }
}
