package vn.saibuoc.shop.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.saibuoc.shop.config.AppProperties;

/**
 * Measures the order queue from the broker's side, so the numbers stay right while the consumer is down:
 * lag (messages not yet processed) and the number of members in the consumer group. Published as the gauges
 * shop.events.lag and shop.events.consumers and shown on the admin simulation page.
 */
@Slf4j
@Component
@ConditionalOnExpression("'${app.events.bootstrap:}'.trim() != ''")
public class OrderQueueMonitor implements DisposableBean {

    /** unsent: orders not handed to the queue yet (SEND_FAILED), counted in the database even while the broker is down. */
    public record Snapshot(String topic, String group, Long lag, Integer consumers, Map<Integer, Long> lagByPartition,
                           Long unsent, Instant at, String error) {
    }

    private final AdminClient admin;
    private final String topic;
    private final String group;
    private final JdbcTemplate jdbc;
    private volatile Snapshot last;

    public OrderQueueMonitor(KafkaAdmin kafkaAdmin, AppProperties props, MeterRegistry meters, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.CLIENT_ID_CONFIG, "shop-backend-queue-monitor");
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8000);
        this.admin = AdminClient.create(config);
        this.topic = props.events().topic();
        this.group = props.events().group();
        this.last = new Snapshot(topic, group, null, null, Map.of(), null, null, "chưa đo");
        Gauge.builder("shop.events.lag", this, m -> m.last.lag() == null ? Double.NaN : m.last.lag())
                .description("Order events not yet processed by the consumer group").tags("topic", topic, "group", group)
                .register(meters);
        Gauge.builder("shop.events.consumers", this, m -> m.last.consumers() == null ? Double.NaN : m.last.consumers())
                .description("Members of the order consumer group").tags("topic", topic, "group", group)
                .register(meters);
        Gauge.builder("shop.events.unsent", this, m -> m.last.unsent() == null ? Double.NaN : m.last.unsent())
                .description("Orders whose event could not be sent to the queue yet").tags("topic", topic)
                .register(meters);
    }

    public Snapshot snapshot() {
        return last;
    }

    @Scheduled(fixedDelay = 10000, initialDelay = 5000)
    public void measure() {
        Long unsent = countUnsent();
        try {
            List<TopicPartition> partitions = admin.describeTopics(List.of(topic)).allTopicNames()
                    .get(8, TimeUnit.SECONDS).get(topic).partitions().stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            Map<TopicPartition, OffsetAndMetadata> committed = admin.listConsumerGroupOffsets(group)
                    .partitionsToOffsetAndMetadata().get(8, TimeUnit.SECONDS);
            Map<TopicPartition, Long> end = offsets(partitions, OffsetSpec.latest());
            Map<TopicPartition, Long> start = offsets(partitions, OffsetSpec.earliest());
            Map<Integer, Long> byPartition = new HashMap<>();
            long lag = 0;
            for (TopicPartition tp : partitions) {
                OffsetAndMetadata c = committed.get(tp);
                long done = c == null ? start.get(tp) : Math.max(c.offset(), start.get(tp));
                long l = Math.max(0, end.get(tp) - done);
                byPartition.put(tp.partition(), l);
                lag += l;
            }
            ConsumerGroupDescription d = admin.describeConsumerGroups(List.of(group)).describedGroups().get(group)
                    .get(8, TimeUnit.SECONDS);
            last = new Snapshot(topic, group, lag, d.members().size(), Map.copyOf(byPartition), unsent, Instant.now(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String msg = e.getCause() == null ? e.toString() : e.getCause().toString();
            if (last.error() == null) {
                log.warn("Cannot measure the order queue: {}", msg);
            }
            last = new Snapshot(topic, group, null, null, Map.of(), unsent, Instant.now(), msg);
        }
    }

    private Long countUnsent() {
        try {
            return jdbc.queryForObject("SELECT count(*) FROM orders WHERE fulfillment_status = 'SEND_FAILED'", Long.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Map<TopicPartition, Long> offsets(List<TopicPartition> partitions, OffsetSpec spec) throws Exception {
        return admin.listOffsets(partitions.stream().collect(Collectors.toMap(tp -> tp, tp -> spec)))
                .all().get(8, TimeUnit.SECONDS).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    @Override
    public void destroy() {
        admin.close();
    }
}
