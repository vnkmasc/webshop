package vn.saibuoc.shop.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.saibuoc.shop.order.OrderService;
import vn.saibuoc.shop.simulation.SimulationService;

/**
 * The warehouse side of the order queue: takes each placed order over (fulfillment RECEIVED, history entry with the
 * time it waited in the queue). Runs in the backend process but in its own consumer group.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.events.bootstrap:}'.trim() != ''")
public class WarehouseConsumer {

    static final String LISTENER_ID = "warehouse";

    private final OrderService orders;
    private final SimulationService simulation;
    private final KafkaListenerEndpointRegistry registry;
    private final ObjectMapper json;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Timer queueTime;

    public WarehouseConsumer(OrderService orders, SimulationService simulation, KafkaListenerEndpointRegistry registry,
                             ObjectMapper json, MeterRegistry meters, Clock clock) {
        this.orders = orders;
        this.simulation = simulation;
        this.registry = registry;
        this.json = json;
        this.meters = meters;
        this.clock = clock;
        this.queueTime = Timer.builder("shop.events.queue.time").description("Time an order event waited in the queue")
                .register(meters);
    }

    @KafkaListener(id = LISTENER_ID, topics = "${app.events.topic}", groupId = "${app.events.group}")
    public void onOrderEvent(ConsumerRecord<String, String> record) throws Exception {
        long delay = simulation.slowConsumerDelayMs();
        if (delay > 0) {
            // KAFKA_SLOW_CONSUMER fault injection: each record takes this much longer to process.
            Thread.sleep(delay);
        }
        OrderEvent event = json.readValue(record.value(), OrderEvent.class);
        Duration waited = Duration.ofMillis(Math.max(0, clock.millis() - record.timestamp()));
        queueTime.record(waited);
        if (!OrderEvent.ORDER_PLACED.equals(event.type())) {
            return;
        }
        boolean taken = orders.warehouseReceived(event.code(), waited);
        meters.counter("shop.events.consumed", "result", taken ? "ok" : "skipped").increment();
        log.info("Warehouse took over order {} from {}-{}@{} after {} ms in the queue", event.code(), record.topic(),
                record.partition(), record.offset(), waited.toMillis());
    }

    /**
     * Applies KAFKA_CONSUMER_DEAD: stops the listener (it leaves the consumer group, so the group has no member)
     * while it is active and starts it again once it is switched off or expires.
     */
    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    public void applyConsumerDead() {
        MessageListenerContainer c = registry.getListenerContainer(LISTENER_ID);
        if (c == null) {
            return;
        }
        boolean dead = simulation.consumerDeadActive();
        if (dead && c.isRunning()) {
            log.warn("Order queue consumer stopped (KAFKA_CONSUMER_DEAD)");
            c.stop(() -> log.warn("Order queue consumer left the group"));
        } else if (!dead && !c.isRunning()) {
            log.warn("Order queue consumer started again");
            c.start();
        }
    }
}
