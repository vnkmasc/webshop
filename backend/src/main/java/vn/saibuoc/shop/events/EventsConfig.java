package vn.saibuoc.shop.events;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.saibuoc.shop.config.AppProperties;

/** Only active when APP_KAFKA_BOOTSTRAP is set; without it the shop runs without the order queue. */
@Configuration
@EnableScheduling
@ConditionalOnExpression("'${app.events.bootstrap:}'.trim() != ''")
public class EventsConfig {

    /** Three partitions so that lag shows per partition; one broker, so one replica. */
    @Bean
    public NewTopic orderTopic(AppProperties props) {
        return TopicBuilder.name(props.events().topic()).partitions(3).replicas(1).build();
    }
}
