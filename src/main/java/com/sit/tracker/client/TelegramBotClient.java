package com.sit.tracker.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TelegramBotClient {

    private final WebClient webClient;

    public TelegramBotClient(
            WebClient.Builder webClientBuilder,
            @Value("${app.telegram.bot-token}") String botToken) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.telegram.org/bot" + botToken)
                .build();
    }

    /**
     * Asynchronously posts HTML-formatted push messages directly to Telegram Bot API.
     * Used for price-alert notifications (no interactive button).
     */
    @Async("telegramAsyncExecutor")
    public void sendPushNotificationAsync(Long chatId, String htmlMessage) {
        log.debug("Dispatching async Telegram push notification to chatId: {}", chatId);

        Map<String, Object> requestBody = Map.of(
                "chat_id", chatId,
                "text", htmlMessage,
                "parse_mode", "HTML",
                "disable_web_page_preview", true
        );

        dispatch(requestBody, chatId, "push notification");
    }

    /**
     * Asynchronously posts an HTML-formatted message with a single inline keyboard
     * button that opens the Telegram Mini App via the native `web_app` context.
     * Used for the /start on-ramp so a real user always has an immediate path
     * into the Mini App interface directly from the chat.
     */
    @Async("telegramAsyncExecutor")
    public void sendWelcomeMessageAsync(Long chatId, String htmlMessage, String buttonLabel, String miniAppUrl) {
        log.debug("Dispatching async Telegram welcome message with Mini App launch button to chatId: {}", chatId);

        Map<String, Object> webAppButton = Map.of(
                "text", buttonLabel,
                "web_app", Map.of("url", miniAppUrl)
        );

        Map<String, Object> replyMarkup = Map.of(
                "inline_keyboard", List.of(List.of(webAppButton))
        );

        Map<String, Object> requestBody = Map.of(
                "chat_id", chatId,
                "text", htmlMessage,
                "parse_mode", "HTML",
                "disable_web_page_preview", true,
                "reply_markup", replyMarkup
        );

        dispatch(requestBody, chatId, "welcome message with Mini App button");
    }

    private void dispatch(Map<String, Object> requestBody, Long chatId, String messageType) {
        this.webClient.post()
                .uri("/sendMessage")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.value() == 429, response -> {
                    log.warn("Telegram API Rate Limit Reached (HTTP 429) for chatId: {}. {} deferred.", chatId, messageType);
                    return Mono.empty();
                })
                .onStatus(status -> status.value() == 403, response -> {
                    log.warn("Telegram Bot Blocked by User (HTTP 403) for chatId: {}. Skipping {}.", chatId, messageType);
                    return Mono.empty();
                })
                .onStatus(HttpStatusCode::isError, response -> {
                    log.error("Telegram API Error (HTTP {}) for chatId: {} sending {}", response.statusCode().value(), chatId, messageType);
                    return Mono.empty();
                })
                .bodyToMono(String.class)
                .doOnSuccess(res -> log.info("Successfully delivered {} to chatId: {}", messageType, chatId))
                .doOnError(err -> log.error("Async Telegram dispatch failed ({}) for chatId: {}: {}", messageType, chatId, err.getMessage()))
                .subscribe();
    }
}