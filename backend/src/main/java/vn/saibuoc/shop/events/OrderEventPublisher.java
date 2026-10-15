package vn.saibuoc.shop.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import vn.saibuoc.shop.config.AppProperties;

/**
 * Sends order events to the queue after checkout. Auxiliary like risk scoring: when the broker is down the order is
 * still placed, only marked SEND_FAILED for the warehouse (checkout waits at most max.block.ms for the broker).
 */
@Slf4j
@Component
public class OrderEventPublisher {

    private final AppProperties.Events props;
    private final ObjectProvider<KafkaTemplate<String, String>> template;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;

    public OrderEventPublisher(AppProperties props, ObjectProvider<KafkaTemplate<String, String>> template,
                               ObjectMapper json, JdbcTemplate jdbc, MeterRegistry meters) {
        this.props = props.events();
        this.template = template;
        this.json = json;
        this.jdbc = jdbc;
        this.meters = meters;
    }

    public boolean enabled() {
        return props != null && props.enabled();
    }

    public void publish(OrderEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            template.getObject().send(props.topic(), event.code(), json.writeValueAsString(event))
                    .whenCompleteAsync((res, e) -> {
                        if (e == null) {
                            meters.counter("shop.events.published", "result", "ok").increment();
                            log.info("Order event {} {} sent to {}-{}@{}", event.type(), event.code(), props.topic(),
                                    res.getRecordMetadata().partition(), res.getRecordMetadata().offset());
                        } else {
                            sendFailed(event, e);
                        }
                    });
        } catch (JsonProcessingException | RuntimeException e) {
            // Broker unreachable: send() itself throws once max.block.ms is over.
            sendFailed(event, e);
        }
    }

    private void sendFailed(OrderEvent event, Throwable e) {
        meters.counter("shop.events.published", "result", "failed").increment();
        log.error("Order event {} {} could not be sent to the queue: {}", event.type(), event.code(), e.getMessage());
        try {
            jdbc.update("UPDATE orders SET fulfillment_status = 'SEND_FAILED', version = version + 1 WHERE code = ? AND fulfillment_status = 'QUEUED'",
                    event.code());
        } catch (RuntimeException ex) {
            log.warn("Could not mark order {} SEND_FAILED: {}", event.code(), ex.getMessage());
        }
    }
}
