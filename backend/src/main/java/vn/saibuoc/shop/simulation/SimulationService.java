package vn.saibuoc.shop.simulation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import vn.saibuoc.shop.config.AppProperties;

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
        FRONTEND_ERROR
    }

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
        states.put(scenario, state);
        log.warn("Simulation scenario {} activated by {} for {} min params={}", scenario, by, minutes, state.params());
        return state;
    }

    public synchronized ScenarioState deactivate(Scenario scenario, String by) {
        states.remove(scenario);
        if (scenario == Scenario.MEMORY_LEAK) {
            retained.clear();
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
        ScenarioState memory = active(Scenario.MEMORY_LEAK);
        if (memory != null) {
            retain(intParam(memory, "kbPerRequest", 512), intParam(memory, "capMb", 256));
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

    /** Thrown on purpose by HTTP_ERROR; handled as an ordinary unexpected server error (500). */
    public static class SimulatedFailureException extends RuntimeException {
        public SimulatedFailureException(String message) {
            super(message);
        }
    }
}
