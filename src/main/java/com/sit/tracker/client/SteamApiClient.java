package com.sit.tracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
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

    // RateLimiter caps how many requests per second leave OUR server toward
    // Steam, regardless of how many users click "Sync" at the same moment —
    // this is what actually protects against a burst of 100+ simultaneous
    // clicks, since the per-user cooldown in InventoryService only prevents
    // ONE user from re-triggering it, not many DIFFERENT users at once.
    @RateLimiter(name = "steamApiLimiter")
    @Retry(name = "steamApiRetry", fallbackMethod = "fetchUserInventoryFallback")
    public Mono<SteamInventoryResponse> fetchUserInventory(String steamId) {
        // Talks to our own Node proxy now (see /steam-proxy), which fetches
        // from Steam on our behalf and already handles the count-param bug,
        // the private/empty-inventory null-body case, and TLS-fingerprint
        // blocking. Route shape is ours to define — kept simple.
        String path = String.format("/inventory/%s", steamId);

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

    // Fallback handler triggered when resilience retry limits are exceeded.
    // TEMPORARY DIAGNOSTIC CHANGE: re-throwing instead of swallowing into an
    // empty response, so the real failure reason reaches UserController's
    // catch block and gets returned to the frontend (previously this was
    // silently returning "0 items synced" on every failure, masking the
    // actual cause). Once the root cause is confirmed and fixed, consider
    // whether silently degrading to an empty inventory is still desired
    // behavior for genuine transient failures.
    public Mono<SteamInventoryResponse> fetchUserInventoryFallback(String steamId, Throwable throwable) {
        log.error("Resilience4j fallback triggered for steamId {}. Reason: {}", steamId, throwable.getMessage(), throwable);
        return Mono.error(throwable);
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SteamInventoryResponse {
        @JsonProperty("assets")
        private List<SteamAsset> assets = Collections.emptyList();

        @JsonProperty("descriptions")
        private List<SteamItemDescription> descriptions = Collections.emptyList();

        @JsonProperty("total_inventory_count")
        private int totalInventoryCount;

        public SteamInventoryResponse() {}

        public SteamInventoryResponse(List<SteamAsset> assets, List<SteamItemDescription> descriptions, int totalInventoryCount) {
            this.assets = assets != null ? assets : Collections.emptyList();
            this.descriptions = descriptions != null ? descriptions : Collections.emptyList();
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