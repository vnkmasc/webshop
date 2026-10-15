package vn.saibuoc.shop.config;

import java.math.BigDecimal;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        boolean seedEnabled,
        boolean simulationEnabled,
        String adminEmail,
        String adminPassword,
        Jwt jwt,
        Shipping shipping,
        Payment payment,
        Risk risk,
        Events events) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Shipping(BigDecimal fee, BigDecimal freeFrom) {
    }

    public record Payment(String url, String apiKey, Duration connectTimeout, Duration readTimeout) {
    }

    /** Order risk scoring service; disabled when url is empty. */
    public record Risk(String url, String apiKey, Duration timeout) {
    }

    /** Order event queue (Kafka); disabled when bootstrap is empty. */
    public record Events(String bootstrap, String topic, String group) {
        public boolean enabled() {
            return bootstrap != null && !bootstrap.isBlank();
        }
    }
}
