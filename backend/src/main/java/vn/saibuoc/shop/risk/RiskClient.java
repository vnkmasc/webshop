package vn.saibuoc.shop.risk;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import vn.saibuoc.shop.config.AppProperties;

/**
 * Client for the order risk scoring service. It is an auxiliary service: any failure or a timeout (default 3 s)
 * returns null and the order goes ahead unscored.
 */
@Slf4j
@Component
public class RiskClient {

    private final RestClient client;
    private final boolean enabled;

    public RiskClient(AppProperties props) {
        AppProperties.Risk risk = props.risk();
        this.enabled = risk != null && risk.url() != null && !risk.url().isBlank();
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(java.time.Duration.ofSeconds(1))
                .withReadTimeout(risk == null || risk.timeout() == null ? java.time.Duration.ofSeconds(3) : risk.timeout());
        this.client = RestClient.builder()
                .baseUrl(enabled ? risk.url() : "http://localhost")
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings))
                .defaultHeader("Authorization", "Bearer " + (risk == null || risk.apiKey() == null ? "" : risk.apiKey()))
                .build();
    }

    public record ScoreRequest(String orderRef, long amount, String paymentMethod, String phone, String province,
                               long avgOrderValue, int phoneOrders24h, List<String> previousProvinces) {
    }

    public record ScoreResult(int score, String level, List<String> reasons) {
    }

    public boolean enabled() {
        return enabled;
    }

    /** The score, or null when the service is not configured, failed or did not answer in time. */
    public ScoreResult score(ScoreRequest req) {
        if (!enabled) {
            return null;
        }
        long start = System.nanoTime();
        try {
            ScoreResult r = client.post().uri("/api/risk/score").contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", rid()).body(req).retrieve().body(ScoreResult.class);
            log.info("Risk score for order {}: {} {} {}", req.orderRef(), r == null ? null : r.score(),
                    r == null ? null : r.level(), r == null ? null : r.reasons());
            return r;
        } catch (RestClientException e) {
            log.warn("Risk service failed for order {} after {} ms, order continues unscored: {}", req.orderRef(),
                    (System.nanoTime() - start) / 1_000_000, e.getMessage());
            return null;
        }
    }

    /** Switches a fault-injection scenario of the risk service (the service keeps the same expiry). */
    public void setScenario(String name, boolean active, Map<String, Object> params, Instant until) {
        if (!enabled) {
            throw new RestClientException("Risk service is not configured (APP_RISK_URL)");
        }
        var body = new java.util.HashMap<String, Object>();
        body.put("active", active);
        body.put("params", params == null ? Map.of() : params);
        body.put("until", until == null ? null : until.toString());
        client.put().uri("/api/admin/simulation/{name}", name).contentType(MediaType.APPLICATION_JSON).body(body)
                .retrieve().toBodilessEntity();
    }

    private static String rid() {
        String rid = MDC.get("requestId");
        return rid == null ? "" : rid;
    }
}
