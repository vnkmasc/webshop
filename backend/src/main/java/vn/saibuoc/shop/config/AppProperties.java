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
        Payment payment) {

    public record Jwt(String secret, Duration ttl) {
    }

    public record Shipping(BigDecimal fee, BigDecimal freeFrom) {
    }

    public record Payment(String url, String apiKey, Duration connectTimeout, Duration readTimeout) {
    }
}
