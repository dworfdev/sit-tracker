package com.sit.tracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class SteamApiClient {

    private final WebClient steamWebClient;

    public SteamApiClient(@Qualifier("steamWebClient") WebClient steamWebClient) {
        this.steamWebClient = steamWebClient;
    }

    @Retry(name = "steamApiRetry", fallbackMethod = "fetchUserInventoryFallback")
    public Mono<SteamInventoryResponse> fetchUserInventory(String steamId) {
        String path = String.format("/inventory/%s/730/2?l=english", steamId);

        return steamWebClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 429) {
                        log.warn("Steam API rate limit encountered (HTTP 429) for steamId: {}", steamId);
                    }
                    return response.createException();
                })
                .bodyToMono(SteamInventoryResponse.class);
    }

    // Fallback handler triggered when resilience retry limits are exceeded
    public Mono<SteamInventoryResponse> fetchUserInventoryFallback(String steamId, Throwable throwable) {
        log.error("Resilience4j fallback triggered for steamId {}. Reason: {}", steamId, throwable.getMessage());
        return Mono.just(new SteamInventoryResponse(Collections.emptyList(), 0));
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamInventoryResponse {
        @JsonProperty("assets")
        private List<SteamAsset> assets; // <-- Добавляем

        @JsonProperty("descriptions")
        private List<SteamItemDescription> descriptions;

        @JsonProperty("total_inventory_count")
        private int totalInventoryCount;

        public SteamInventoryResponse() {}

        public SteamInventoryResponse(List<SteamItemDescription> descriptions, int totalInventoryCount) {
            this.descriptions = descriptions;
            this.totalInventoryCount = totalInventoryCount;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamAsset {
        @JsonProperty("classid")
        private String classId;

        @JsonProperty("instanceid")
        private String instanceId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamItemDescription {
        @JsonProperty("appid")
        private int appId;

        @JsonProperty("classid")
        private String classId;

        @JsonProperty("instanceid")
        private String instanceId;

        @JsonProperty("market_hash_name")
        private String marketHashName;

        @JsonProperty("icon_url")
        private String iconUrl;

        @JsonProperty("tradable")
        private int tradable;
    }
}