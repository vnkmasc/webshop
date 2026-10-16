package vn.saibuoc.shop.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.saibuoc.shop.config.AppProperties;

/**
 * Sends order events to the queue after checkout. Auxiliary like risk scoring: when the broker is down the order is
 * still placed, only marked SEND_FAILED for the warehouse; {@link OrderEventRelay} sends it again later.
 * Checkout waits up to app.events.send-timeout for the broker to confirm, so a broken queue shows in the checkout
 * request itself (slower, an {@link OrderEventException} in its trace, header X-Order-Queue: FAILED) instead of
 * failing silently in a background thread.
 */
@Slf4j
@Component
public class OrderEventPublisher {

    /** The event could not be handed to the broker in time. */
    public static class OrderEventException extends Exception {
        public OrderEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

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

    /** Checkout: sends the event; on failure marks the order SEND_FAILED. Never throws. */
    public void publish(OrderEvent event) {
        if (!enabled()) {
            return;
        }
        try {
            send(event);
            responseHeader("SENT");
        } catch (OrderEventException e) {
            responseHeader("FAILED");
            log.error("Order event {} {} could not be sent to the queue, the warehouse will get it later: {}",
                    event.type(), event.code(), e.getMessage(), e);
            markSendFailed(event.code());
        }
    }

    /** Sends one event and waits for the broker's confirmation (at most send-timeout, plus max.block.ms). */
    public void send(OrderEvent event) throws OrderEventException {
        try {
            SendResult<String, String> res = template.getObject()
                    .send(props.topic(), event.code(), json.writeValueAsString(event))
                    .get(props.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
            meters.counter("shop.events.published", "result", "ok").increment();
            log.info("Order event {} {} sent to {}-{}@{}", event.type(), event.code(), props.topic(),
                    res.getRecordMetadata().partition(), res.getRecordMetadata().offset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("interrupted", e);
        } catch (ExecutionException e) {
            throw failed("broker rejected the event: " + e.getCause(), e.getCause());
        } catch (java.util.concurrent.TimeoutException e) {
            throw failed("no confirmation from the broker within " + props.sendTimeout().toMillis() + " ms", e);
        } catch (JsonProcessingException | RuntimeException e) {
            // Broker unreachable without metadata: send() itself throws once max.block.ms is over.
            throw failed(e.toString(), e);
        }
    }

    private OrderEventException failed(String message, Throwable cause) {
        meters.counter("shop.events.published", "result", "failed").increment();
        return new OrderEventException("Order queue unavailable: " + message, cause);
    }

    private void markSendFailed(String code) {
        try {
            jdbc.update("UPDATE orders SET fulfillment_status = 'SEND_FAILED', version = version + 1 WHERE code = ? AND fulfillment_status = 'QUEUED'",
                    code);
        } catch (RuntimeException ex) {
            log.warn("Could not mark order {} SEND_FAILED: {}", code, ex.getMessage());
        }
    }

    private static void responseHeader(String value) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a && a.getResponse() != null) {
            a.getResponse().setHeader("X-Order-Queue", value);
        }
    }
}
