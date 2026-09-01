package com.sit.tracker.scheduler;

import com.sit.tracker.client.PriceApiClient;
import com.sit.tracker.repository.TrackedItemRepository;
import com.sit.tracker.service.PriceCalculationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceSyncScheduler {

    private final TrackedItemRepository trackedItemRepository;
    private final PriceApiClient priceApiClient;
    private final PriceCalculationService priceCalculationService;

    /**
     * Executes at fixed intervals to update prices for all actively monitored items.
     * Pulls a single bulk price snapshot from the aggregator, then applies each
     * monitored item's real price against PriceCalculationService.
     *
     * NOTE: priceApiClient.fetchMarketPrices() already has a Resilience4j-backed
     * retry + fallback (returns an empty price map on repeated failure), so this
     * scheduler does not need its own retry/backoff logic — it only needs to
     * handle "no data returned" gracefully, which it does below.
     */
    @Scheduled(fixedRateString = "${app.scheduling.price-sync-rate:3600000}")
    public void executePriceMonitoringCycle() {
        log.info("Starting background Price Monitoring Cron Cycle...");

        List<String> monitoredItemNames = trackedItemRepository.findDistinctMonitoredItemNames();
        if (monitoredItemNames.isEmpty()) {
            log.info("Cron Cycle completed: No items are currently marked as monitored.");
            return;
        }

        log.info("Extracted {} unique monitored item(s) for bulk price query.", monitoredItemNames.size());

        try {
            PriceApiClient.MarketPriceResponse response = priceApiClient.fetchMarketPrices().block();
            applyPriceUpdates(response, monitoredItemNames);
            log.info("Price Monitoring Cron Cycle completed successfully.");
        } catch (Exception e) {
            // Defensive catch only — fetchMarketPrices() already falls back internally.
            // This guards against an unexpected exception escaping the reactive chain.
            log.error("Unexpected failure during Price Monitoring Cron Cycle: {}", e.getMessage(), e);
        }
    }

    private void applyPriceUpdates(PriceApiClient.MarketPriceResponse response, List<String> monitoredItemNames) {
        if (response == null || response.getPrices() == null || response.getPrices().isEmpty()) {
            log.warn("Price aggregator returned no pricing data for this cycle. Skipping updates — " +
                    "no mock or fallback price will be applied.");
            return;
        }

        Map<String, PriceApiClient.ItemPriceData> priceMap = response.getPrices();
        int updated = 0;
        int skipped = 0;

        for (String itemName : monitoredItemNames) {
            PriceApiClient.ItemPriceData priceData = priceMap.get(itemName);

            if (priceData == null || priceData.getPrice() == null) {
                log.debug("No upstream price entry found for monitored item: '{}'. Skipping — " +
                        "existing stored price is left untouched.", itemName);
                skipped++;
                continue;
            }

            BigDecimal realPrice = priceData.getPrice();

            try {
                priceCalculationService.processPriceUpdate(itemName, realPrice);
                updated++;
            } catch (IllegalArgumentException e) {
                // Item existed in monitored-names query but was removed/renamed between
                // the query and this write — log and continue the batch, don't abort it.
                log.warn("Skipped price update for '{}': {}", itemName, e.getMessage());
                skipped++;
            }
        }

        log.info("Price cycle applied {} real price update(s), skipped {} item(s) with no upstream data.",
                updated, skipped);
    }
}