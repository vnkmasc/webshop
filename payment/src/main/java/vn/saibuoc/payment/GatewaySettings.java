package vn.saibuoc.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Behaviour of the simulated acquirer, changeable at runtime from the shop admin.
 *
 * @param baseLatencyMs      normal processing time
 * @param extraLatencyMs     additional delay added to every charge (simulates a slow bank)
 * @param errorRatePercent   share of charges that fail with a server error
 * @param declineRatePercent share of otherwise valid charges that the bank declines
 * @param outage             every request is answered with 503
 */
public record GatewaySettings(
        @Min(0) @Max(5000) int baseLatencyMs,
        @Min(0) @Max(60000) int extraLatencyMs,
        @Min(0) @Max(100) int errorRatePercent,
        @Min(0) @Max(100) int declineRatePercent,
        boolean outage) {

    public static GatewaySettings defaults() {
        return new GatewaySettings(250, 0, 0, 3, false);
    }
}
