package com.sit.tracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@Component
public class PriceApiClient {

    private final WebClient priceAggregatorWebClient;

    @Value("${app.price-aggregator.api-key:mock_key}")
    private String apiKey;

    public PriceApiClient(@Qualifier("priceAggregatorWebClient") WebClient priceAggregatorWebClient) {
        this.priceAggregatorWebClient = priceAggregatorWebClient;
    }

    @Retry(name = "priceApiRetry", fallbackMethod = "fetchMarketPricesFallback")
    public Mono<MarketPriceResponse> fetchMarketPrices() {
        return priceAggregatorWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/prices")
                        .queryParam("api_key", apiKey)
                        .build())
                .retrieve()
                .bodyToMono(MarketPriceResponse.class);
    }

    public Mono<MarketPriceResponse> fetchMarketPricesFallback(Throwable throwable) {
        log.error("Resilience4j fallback triggered for Price Aggregator. Reason: {}", throwable.getMessage());
        return Mono.just(new MarketPriceResponse(Map.of()));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MarketPriceResponse {
        @JsonProperty("prices")
        private Map<String, ItemPriceData> prices;

        public MarketPriceResponse() {}

        public MarketPriceResponse(Map<String, ItemPriceData> prices) {
            this.prices = prices;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemPriceData {
        @JsonProperty("price")
        private BigDecimal price;

        @JsonProperty("market")
        private String market;

        @JsonProperty("last_updated")
        private Long lastUpdated;
    }
}