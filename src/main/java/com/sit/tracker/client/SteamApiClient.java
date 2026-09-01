package com.sit.tracker.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SteamApiClient(@Qualifier("steamWebClient") WebClient steamWebClient) {
        this.steamWebClient = steamWebClient;
    }

    @Retry(name = "steamApiRetry", fallbackMethod = "fetchUserInventoryFallback")
    public Mono<SteamInventoryResponse> fetchUserInventory(String steamId) {
        String path = String.format("/inventory/%s/730/2?l=english&count=5000", steamId);

        // TEMPORARY DIAGNOSTIC: read the body as a raw String first and log it
        // in full, instead of letting WebClient deserialize straight into
        // SteamInventoryResponse. Direct deserialization can silently coerce
        // a shape mismatch into empty lists (fields default to
        // Collections.emptyList() and unknown properties are ignored), which
        // is indistinguishable from "Steam genuinely returned nothing." This
        // makes the raw upstream response visible in logs either way, so we
        // can tell whether the fault is upstream (Steam/network) or in our
        // own mapping logic in UserController.
        return steamWebClient.get()
                .uri(path)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, response -> {
                    if (response.statusCode().value() == 429) {
                        log.warn("Steam API rate limit encountered (HTTP 429) for steamId: {}", steamId);
                    }
                    return response.createException();
                })
                .bodyToMono(String.class)
                .doOnNext(raw -> log.info(
                        "RAW Steam inventory response for steamId {} ({} chars): {}",
                        steamId,
                        raw.length(),
                        raw.length() > 2000 ? raw.substring(0, 2000) + "...[truncated]" : raw
                ))
                .map(raw -> {
                    try {
                        return objectMapper.readValue(raw, SteamInventoryResponse.class);
                    } catch (Exception e) {
                        log.error("Failed to deserialize Steam inventory JSON for steamId {}: {}", steamId, e.getMessage());
                        throw new RuntimeException("Steam inventory JSON parse failure: " + e.getMessage(), e);
                    }
                });
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