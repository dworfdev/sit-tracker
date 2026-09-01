package com.sit.tracker.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sit.tracker.client.TelegramBotClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/telegram")
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final TelegramBotClient telegramBotClient;

    @Value("${app.telegram.webhook-secret:#{null}}")
    private String expectedSecretToken;

    @Value("${app.telegram.mini-app-web-url}")
    private String miniAppUrl;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleTelegramUpdate(
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken,
            @RequestBody String rawUpdateJson) {

        // Strict Fail-Closed Verification Strategy
        if (expectedSecretToken == null || expectedSecretToken.isBlank() || !expectedSecretToken.equals(secretToken)) {
            log.warn("Security Alert: Webhook invocation rejected. Invalid, missing, or unconfigured secret token header.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Secret token mismatch or unconfigured perimeter security"));
        }

        try {
            JsonNode updateNode = objectMapper.readTree(rawUpdateJson);
            if (updateNode.has("message")) {
                processInboundMessage(updateNode.get("message"));
            }
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("Failed to parse incoming Telegram update payload", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Malformed update payload"));
        }
    }

    private void processInboundMessage(JsonNode messageNode) {
        if (!messageNode.has("text")) {
            return;
        }

        String text = messageNode.get("text").asText().trim();
        long chatId = messageNode.path("chat").path("id").asLong();
        String username = messageNode.path("from").path("username").asText("there");

        log.info("Received inbound command '{}' from chatId: {} (@{})", text, chatId, username);

        if (text.startsWith("/start")) {
            handleStartCommand(chatId, username);
        } else if (text.startsWith("/help")) {
            handleHelpCommand(chatId);
        } else if (text.startsWith("/tier")) {
            handleTierCommand(chatId);
        } else {
            handleUnknownCommand(chatId);
        }
    }

    private void handleStartCommand(long chatId, String username) {
        log.info("Routing /start on-ramp to chatId {}", chatId);

        // CONTENT: confirm final copy against SIT Management Doc localization pass (RUS/ENG) when ready.
        String welcomeMessage = String.format(
                "👋 <b>Welcome to Steam Inventory Tracker, %s!</b>\n\n" +
                        "Track your CS2 inventory value in real time, set price-shift alerts, " +
                        "and monitor your portfolio — all inside Telegram.\n\n" +
                        "Tap the button below to open your dashboard.",
                username
        );

        telegramBotClient.sendWelcomeMessageAsync(chatId, welcomeMessage, "🚀 Open Portfolio", miniAppUrl);
    }

    private void handleHelpCommand(long chatId) {
        log.info("Routing /help response to chatId {}", chatId);

        String helpMessage =
                "<b>Steam Inventory Tracker — Commands</b>\n\n" +
                        "/start — Open your portfolio dashboard\n" +
                        "/tier — Check your current monitoring tier and limits\n" +
                        "/help — Show this message";

        telegramBotClient.sendPushNotificationAsync(chatId, helpMessage);
    }

    private void handleTierCommand(long chatId) {
        log.info("Routing /tier status response to chatId {}", chatId);
        // Deferred to Phase 9.1 (Pro-Tier Gating Validation) — requires resolving
        // the persisted User.isPremium flag via chatId before this can respond meaningfully.
    }

    private void handleUnknownCommand(long chatId) {
        log.debug("Unrecognized text command received from chatId {}", chatId);
    }
}