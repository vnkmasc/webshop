package vn.saibuoc.shop.config;

import java.time.Clock;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Client for the payment gateway service (separate process, see PAYMENT_URL). */
    @Bean
    public RestClient paymentRestClient(AppProperties props) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(props.payment().connectTimeout())
                .withReadTimeout(props.payment().readTimeout());
        return RestClient.builder()
                .baseUrl(props.payment().url())
                .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings))
                .defaultHeader("Authorization", "Bearer " + (props.payment().apiKey() == null ? "" : props.payment().apiKey()))
                .build();
    }
}
