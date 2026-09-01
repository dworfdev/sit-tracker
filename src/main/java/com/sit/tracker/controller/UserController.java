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
import java.util.stream.Collectors;

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

        // Server-side cooldown check — cannot be bypassed by refreshing the
        // page, using another device, or calling the API directly, unlike
        // the client-side cooldown in app.js. Throws IllegalStateException,
        // caught below and returned as 429.
        inventoryService.assertSyncAllowed(userId);

        try {
            List<String> extractedItems = steamApiClient.fetchUserInventory(steamId)
                    .map(response -> {
                        if (response == null || response.getAssets() == null || response.getDescriptions() == null) {
                            return Collections.<String>emptyList();
                        }

                        // 1. Собираем карту описаний: "classid_instanceid" -> market_hash_name
                        Map<String, String> descMap = response.getDescriptions().stream()
                                .filter(d -> d.getMarketHashName() != null)
                                .collect(Collectors.toMap(
                                        d -> d.getClassId() + "_" + d.getInstanceId(),
                                        SteamApiClient.SteamItemDescription::getMarketHashName,
                                        (existing, replacement) -> existing
                                ));

                        // 2. Для каждого ассета достаем его реальное имя из карты
                        return response.getAssets().stream()
                                .map(asset -> descMap.get(asset.getClassId() + "_" + asset.getInstanceId()))
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
            log.error("Steam inventory sync failure for user {}: ", userId, e);

            String message = e.getMessage() != null && e.getMessage().contains("429")
                    ? "Steam is rate-limiting inventory requests right now. Please wait a few minutes before syncing again."
                    : "Failed to process Steam inventory: " + e.getMessage();

            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", message));
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