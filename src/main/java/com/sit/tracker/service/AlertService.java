package com.sit.tracker.service;

import com.sit.tracker.client.TelegramBotClient;
import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.repository.UserInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final UserInventoryRepository userInventoryRepository;
    private final TelegramBotClient telegramBotClient;

    // FIXED: was "app.telegram.mini-app-url" — renamed to match the Phase 7
    // config key split. This is the plain t.me deep-link format, correct for
    // use inside an <a href> tag in a push notification (NOT the web_app
    // button URL, which now lives under app.telegram.mini-app-web-url and is
    // only consumed by TelegramWebhookController's /start handler).
    @Value("${app.telegram.mini-app-deeplink:https://t.me/YourBotName/app}")
    private String miniAppDeeplink;

    public void processPriceAlerts(TrackedItem item, BigDecimal oldPrice, BigDecimal newPrice, double deltaPercentage) {
        if (Math.abs(deltaPercentage) < 5.0) {
            log.debug("Delta percentage {}% is below threshold (5.0%). No alerts triggered.", deltaPercentage);
            return;
        }

        List<Long> targetUserIds = userInventoryRepository.findUserIdsByMonitoredItemId(item.getId());
        if (targetUserIds.isEmpty()) {
            log.info("No active users monitoring item ID: {} ('{}'). Skipping alert dispatch.", item.getId(), item.getMarketHashName());
            return;
        }

        log.info("Threshold breach detected for '{}' ({}%). Resolving {} target user(s).",
                item.getMarketHashName(), deltaPercentage, targetUserIds.size());

        String formattedMessage = formatAlertMessage(item.getMarketHashName(), oldPrice, newPrice, deltaPercentage);

        for (Long userId : targetUserIds) {
            telegramBotClient.sendPushNotificationAsync(userId, formattedMessage);
        }
    }

    public String formatAlertMessage(String itemName, BigDecimal oldPrice, BigDecimal newPrice, double deltaPercentage) {
        String directionEmoji = deltaPercentage > 0 ? "📈" : "📉";
        String sign = deltaPercentage > 0 ? "+" : "";

        return String.format(
                "%s <b>Market Alert: Price Shift Detected!</b>\n\n" +
                        "<b>Item:</b> %s\n" +
                        "<b>Old Price:</b> $%s\n" +
                        "<b>New Price:</b> $%s\n" +
                        "<b>Change:</b> %s%.2f%%\n\n" +
                        "👉 <a href=\"%s\">Open Portfolio in Mini App</a>",
                directionEmoji,
                itemName,
                oldPrice.setScale(2).toString(),
                newPrice.setScale(2).toString(),
                sign,
                deltaPercentage,
                miniAppDeeplink
        );
    }
}