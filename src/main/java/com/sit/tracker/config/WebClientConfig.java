package com.sit.tracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${app.steam.api-base-url:https://steamcommunity.com}")
    private String steamApiBaseUrl;

    @Value("${app.price-aggregator.base-url:https://api.pricempire.com}")
    private String priceAggregatorBaseUrl;

    @Bean
    public WebClient steamWebClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl(steamApiBaseUrl)
                .build();
    }

    @Bean
    public WebClient priceAggregatorWebClient(WebClient.Builder sharedBuilder) {
        return sharedBuilder.clone()
                .baseUrl(priceAggregatorBaseUrl)
                .build();
    }
}