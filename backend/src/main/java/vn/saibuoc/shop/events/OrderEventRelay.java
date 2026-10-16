package vn.saibuoc.shop.events;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.saibuoc.shop.order.OrderService;

/**
 * Sends the events of orders marked SEND_FAILED again once the broker is back, oldest first, so no order is lost
 * for the warehouse while the queue is down. Stops at the first failure and tries again next interval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnExpression("'${app.events.bootstrap:}'.trim() != ''")
public class OrderEventRelay {

    private static final int BATCH = 50;

    private final OrderService orders;
    private final OrderEventPublisher publisher;

    @Scheduled(fixedDelayString = "${app.events.relay-interval:PT30S}", initialDelay = 20000)
    public void relay() {
        List<OrderEvent> pending = orders.unsentEvents(BATCH);
        int sent = 0;
        for (OrderEvent event : pending) {
            try {
                publisher.send(event);
            } catch (OrderEventPublisher.OrderEventException e) {
                log.warn("Order queue still unavailable, orders waiting for the warehouse: {} or more ({})",
                        pending.size() - sent, e.getMessage());
                break;
            }
            orders.requeued(event.code());
            sent++;
        }
        if (sent > 0) {
            log.info("Re-sent {} order events to the queue", sent);
        }
    }
}
