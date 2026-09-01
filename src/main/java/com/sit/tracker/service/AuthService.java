package com.sit.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AuthService {

    private static final long AUTH_EXPIRATION_SECONDS = 86400; // 24 Hours

    @Value("${app.telegram.bot-token}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean validateInitData(String initData) {
        if (initData == null || initData.isBlank()) {
            log.warn("Authentication failed: Missing or blank initData");
            return false;
        }

        try {
            Map<String, String> queryParams = parseQueryString(initData);
            String hash = queryParams.remove("hash");
            if (hash == null) {
                log.warn("Authentication failed: Hash parameter missing from initData");
                return false;
            }

            // Freshness Verification (auth_date check)
            String authDateStr = queryParams.get("auth_date");
            if (authDateStr == null) {
                log.warn("Authentication failed: auth_date missing");
                return false;
            }

            long authDate = Long.parseLong(authDateStr);
            long currentTime = Instant.now().getEpochSecond();
            if ((currentTime - authDate) > AUTH_EXPIRATION_SECONDS) {
                log.warn("Authentication failed: Expired initData payload (auth_date: {}, current: {})", authDate, currentTime);
                return false;
            }

            // HMAC-SHA256 Checksum Calculation
            String dataCheckString = queryParams.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining("\n"));

            byte[] secretKey = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, "WebAppData").hmac(botToken);
            String calculatedHash = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, secretKey).hmacHex(dataCheckString);

            boolean matches = calculatedHash.equalsIgnoreCase(hash);
            if (!matches) {
                log.warn("Authentication failed: Invalid HMAC signature");
            }
            return matches;
        } catch (Exception e) {
            log.error("Authentication process encountered an error: {}", e.getMessage(), e);
            return false;
        }
    }

    public Long extractUserId(String initData) {
        if (!validateInitData(initData)) {
            throw new SecurityException("Unauthorized: Invalid Telegram authentication payload");
        }
        try {
            Map<String, String> queryParams = parseQueryString(initData);
            String userJson = queryParams.get("user");
            if (userJson == null) {
                throw new IllegalArgumentException("User context missing in initData payload");
            }
            JsonNode userNode = objectMapper.readTree(userJson);
            return userNode.get("id").asLong();
        } catch (Exception e) {
            log.error("Failed to extract User ID from initData context: {}", e.getMessage());
            throw new SecurityException("Failed to resolve user session context", e);
        }
    }

    private Map<String, String> parseQueryString(String queryString) {
        return Arrays.stream(queryString.split("&"))
                .map(param -> param.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                        pair -> pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "",
                        (existing, replacement) -> existing
                ));
    }
}