package com.sit.tracker.service;

import com.sit.tracker.entity.PriceHistory;
import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.repository.PriceHistoryRepository;
import com.sit.tracker.repository.TrackedItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceCalculationService {

    private final TrackedItemRepository trackedItemRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    /**
     * Calculates relative percentage delta safely:
     * Delta = ((NewPrice - OldPrice) / OldPrice) * 100
     */
    public Optional<Double> calculatePercentageDelta(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null) {
            log.debug("Skipping delta calculation: One or both price parameters are null.");
            return Optional.empty();
        }

        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("Skipping delta calculation: Division by zero avoided for old price = 0.00.");
            return Optional.empty();
        }

        BigDecimal difference = newPrice.subtract(oldPrice);
        BigDecimal deltaFraction = difference.divide(oldPrice, 4, RoundingMode.HALF_UP);
        double percentageDelta = deltaFraction.multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

        log.debug("Calculated price delta: {}% (Old: {}, New: {})", percentageDelta, oldPrice, newPrice);
        return Optional.of(percentageDelta);
    }

    @Transactional
    public Optional<Double> processPriceUpdate(String marketHashName, BigDecimal newPrice) {
        log.info("Processing price update for item: '{}' with new price: ${}", marketHashName, newPrice);

        TrackedItem item = trackedItemRepository.findByMarketHashName(marketHashName)
                .orElseThrow(() -> new IllegalArgumentException("Tracked item not found: " + marketHashName));

        BigDecimal oldPrice = item.getCurrentPrice();
        item.setCurrentPrice(newPrice);
        trackedItemRepository.save(item);

        PriceHistory snapshot = PriceHistory.builder()
                .item(item)
                .price(newPrice)
                .build();
        priceHistoryRepository.save(snapshot);
        log.debug("Persisted new price_history snapshot ID: {} for item: '{}'", snapshot.getId(), marketHashName);

        Optional<Double> deltaOpt = calculatePercentageDelta(oldPrice, newPrice);
        if (deltaOpt.isPresent()) {
            double absoluteDelta = Math.abs(deltaOpt.get());
            if (absoluteDelta >= 5.0) {
                log.info("ALERT THRESHOLD BREACHED: Item '{}' shifted by {}% (Threshold: >= 5.0%)", marketHashName, deltaOpt.get());
            } else {
                log.debug("Item '{}' price change of {}% is within normal operating limits (< 5.0%).", marketHashName, deltaOpt.get());
            }
        }

        return deltaOpt;
    }
}