package com.sit.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // Points at our own Node proxy service now (see /steam-proxy), not Steam
    // directly. The Java reactor-netty client's TLS fingerprint gets a flat
    // 429 from Steam regardless of IP, target account, or headers — confirmed
    // via controlled testing — while Node's TLS stack passes fine. Rather
    // than fighting TLS fingerprinting in Java, the Node proxy does the
    // actual Steam call and we talk to it over a normal internal HTTP call.
    @Value("${app.steam.proxy-base-url}")
    private String steamProxyBaseUrl;

    @Value("${app.steam.proxy-internal-secret:}")
    private String proxyInternalSecret;

    @Value("${app.price-aggregator.base-url:https://api.pricempire.com}")
    private String priceAggregatorBaseUrl;

    @Bean
    public WebClient steamWebClient(WebClient.Builder sharedBuilder) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        WebClient.Builder builder = sharedBuilder.clone()
                .baseUrl(steamProxyBaseUrl)
                .exchangeStrategies(strategies);

        if (proxyInternalSecret != null && !proxyInternalSecret.isBlank()) {
            builder.defaultHeader("X-Internal-Secret", proxyInternalSecret);
        }

        return builder.build();
    }

    @Bean
    public WebClient priceAggregatorWebClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl(priceAggregatorBaseUrl)
                .build();
    }
}