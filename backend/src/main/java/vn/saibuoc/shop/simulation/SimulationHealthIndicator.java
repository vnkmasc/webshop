package vn.saibuoc.shop.simulation;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health contributor "simulation", part of the readiness group only (see application.yml): DOWN while the
 * READINESS_FAIL scenario is on. Liveness does not include it, so supervisors do not restart the process.
 */
@Component("simulationHealthIndicator")
@RequiredArgsConstructor
public class SimulationHealthIndicator implements HealthIndicator {

    private final SimulationService simulation;

    @Override
    public Health health() {
        if (simulation.readinessFailActive()) {
            return Health.down().withDetail("reason", "READINESS_FAIL scenario is active").build();
        }
        return Health.up().build();
    }
}
