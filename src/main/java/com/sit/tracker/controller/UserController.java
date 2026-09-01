package com.sit.tracker.controller;

import com.sit.tracker.client.SteamApiClient;
import com.sit.tracker.entity.UserInventory;
import com.sit.tracker.service.AuthService;
import com.sit.tracker.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final InventoryService inventoryService;
    private final SteamApiClient steamApiClient;

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> authenticate(@RequestHeader("X-Telegram-Init-Data") String initData) {
        boolean isValid = authService.validateInitData(initData);
        if (!isValid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired Telegram initData signature"));
        }
        Long userId = authService.extractUserId(initData);
        return ResponseEntity.ok(Map.of("status", "authenticated", "userId", userId));
    }

    @PostMapping("/steam-link")
    public ResponseEntity<Map<String, String>> linkSteam(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam String steamId) {
        Long userId = authService.extractUserId(initData);
        inventoryService.linkSteamId(userId, steamId);
        return ResponseEntity.ok(Map.of("message", "Steam ID linked successfully"));
    }

    @PostMapping("/sync-inventory")
    public ResponseEntity<Map<String, String>> syncInventory(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam String steamId) {
        Long userId = authService.extractUserId(initData);
        try {
            // Directly consuming typed reactive model and transforming to market hash names
            List<String> extractedItems = steamApiClient.fetchUserInventory(steamId)
                    .map(response -> {
                        if (response == null || response.getDescriptions() == null) {
                            return Collections.<String>emptyList();
                        }
                        return response.getDescriptions().stream()
                                .map(SteamApiClient.SteamItemDescription::getMarketHashName)
                                .filter(Objects::nonNull)
                                .toList();
                    })
                    .block();

            if (extractedItems == null) {
                extractedItems = Collections.emptyList();
            }

            inventoryService.processAndSyncInventory(userId, extractedItems);
            return ResponseEntity.ok(Map.of(
                    "message", "Inventory synchronized successfully",
                    "itemsSynced", String.valueOf(extractedItems.size())
            ));
        } catch (Exception e) {
            log.error("Steam inventory sync failure for user {}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "Failed to process Steam inventory: " + e.getMessage()));
        }
    }

    @GetMapping("/dashboard")
    public ResponseEntity<List<UserInventory>> getDashboard(@RequestHeader("X-Telegram-Init-Data") String initData) {
        Long userId = authService.extractUserId(initData);
        return ResponseEntity.ok(inventoryService.getUserDashboard(userId));
    }

    @PostMapping("/toggle-monitor")
    public ResponseEntity<Map<String, Object>> toggleMonitor(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam Long itemId,
            @RequestParam boolean enable) {
        Long userId = authService.extractUserId(initData);
        try {
            boolean result = inventoryService.toggleItemMonitoring(userId, itemId, enable);
            return ResponseEntity.ok(Map.of("itemId", itemId, "isMonitored", result));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}