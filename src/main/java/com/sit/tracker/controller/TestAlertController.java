package com.sit.tracker.controller;

import com.sit.tracker.client.SteamApiClient;
import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.repository.TrackedItemRepository;
import com.sit.tracker.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/test")
@Profile("!prod")
@RequiredArgsConstructor
public class TestAlertController {

    private final TrackedItemRepository trackedItemRepository;
    private final AlertService alertService;
    private final SteamApiClient steamApiClient;

    /**
     * TEMPORARY DIAGNOSTIC ENDPOINT — checks whether the network this app is
     * currently running on (e.g. home IP vs a cloud host's IP) gets rate
     * limited by Steam. Run the app locally (default/dev profile, no DB
     * config needed for this test) and hit:
     *   GET http://localhost:8080/api/v1/test/steam-raw?steamId=76561198041683378
     * If this succeeds locally but fails with 429 on Render, that confirms
     * the block is IP-based on Render's side, not a bug in our request logic.
     * Remove once the IP hypothesis is confirmed/ruled out.
     */
    @GetMapping("/steam-raw")
    public ResponseEntity<Object> testSteamRaw(@RequestParam String steamId) {
        try {
            SteamApiClient.SteamInventoryResponse response = steamApiClient.fetchUserInventory(steamId).block();
            int assetCount = response != null && response.getAssets() != null ? response.getAssets().size() : -1;
            int descCount = response != null && response.getDescriptions() != null ? response.getDescriptions().size() : -1;
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "assetCount", assetCount,
                    "descriptionCount", descCount
            ));
        } catch (Exception e) {
            log.error("steam-raw diagnostic call failed", e);
            return ResponseEntity.status(502).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/trigger-alert")
    public ResponseEntity<Map<String, Object>> triggerTestAlert(
            @RequestParam Long itemId,
            @RequestParam double deltaPercentage) {

        log.info("Executing test alert trigger for Item ID: {} with delta: {}%", itemId, deltaPercentage);

        TrackedItem item = trackedItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        BigDecimal oldPrice = item.getCurrentPrice();
        BigDecimal multiplier = BigDecimal.valueOf(1 + (deltaPercentage / 100.0));
        BigDecimal newPrice = oldPrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);

        item.setCurrentPrice(newPrice);
        trackedItemRepository.save(item);

        alertService.processPriceAlerts(item, oldPrice, newPrice, deltaPercentage);

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "item", item.getMarketHashName(),
                "oldPrice", oldPrice,
                "newPrice", newPrice,
                "deltaPercentage", deltaPercentage
        ));
    }
}