package com.sit.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.steam.api-base-url:https://steamcommunity.com}")
    private String steamApiBaseUrl;

    @Value("${app.price-aggregator.base-url:https://api.pricempire.com}")
    private String priceAggregatorBaseUrl;

    @Bean
    public WebClient steamWebClient(WebClient.Builder sharedBuilder) {
        // Увеличиваем буфер памяти до 10 МБ, чтобы парсить большие JSON без DataBufferLimitException
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        return sharedBuilder.clone()
                .baseUrl(steamApiBaseUrl)
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
    }

    @Bean
    public WebClient priceAggregatorWebClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl(priceAggregatorBaseUrl)
                .build();
    }
}