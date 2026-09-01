package com.sit.tracker.controller;

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