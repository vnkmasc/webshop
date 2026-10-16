package vn.saibuoc.shop.events;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Health component "orderQueue" of /actuator/health: DOWN while the broker cannot be reached. Not part of the
 * liveness or readiness groups: the shop keeps selling without the queue, so it must not be restarted or taken out
 * of the load balancer for it; only the overall health (and anything that checks it) reports the problem.
 */
@Component("orderQueue")
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.events.bootstrap:}'.trim() != ''")
public class OrderQueueHealth implements HealthIndicator {

    private final OrderQueueMonitor monitor;

    @Override
    public Health health() {
        OrderQueueMonitor.Snapshot s = monitor.snapshot();
        Health.Builder h = s.error() == null ? Health.up() : Health.down().withDetail("error", s.error());
        h.withDetail("topic", s.topic()).withDetail("group", s.group());
        if (s.lag() != null) {
            h.withDetail("lag", s.lag()).withDetail("consumers", s.consumers());
        }
        if (s.unsent() != null) {
            h.withDetail("unsent", s.unsent());
        }
        return h.build();
    }
}
