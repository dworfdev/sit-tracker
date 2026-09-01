package com.sit.tracker.service;

import com.sit.tracker.client.TelegramBotClient;
import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.repository.UserInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private UserInventoryRepository userInventoryRepository;

    @Mock
    private TelegramBotClient telegramBotClient;

    @InjectMocks
    private AlertService alertService;

    private TrackedItem testItem;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(alertService, "miniAppUrl", "https://t.me/YourBotName/app");

        testItem = TrackedItem.builder()
                .id(100L)
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .currentPrice(BigDecimal.valueOf(12.00))
                .build();
    }

    @Test
    @DisplayName("Should dispatch async alerts when price delta >= 5.0%")
    void testProcessPriceAlerts_BreachTriggersAlerts() {
        when(userInventoryRepository.findUserIdsByMonitoredItemId(100L))
                .thenReturn(List.of(1111L, 2222L));

        alertService.processPriceAlerts(testItem, BigDecimal.valueOf(10.00), BigDecimal.valueOf(12.00), 20.0);

        verify(userInventoryRepository, times(1)).findUserIdsByMonitoredItemId(100L);
        verify(telegramBotClient, times(2)).sendPushNotificationAsync(anyLong(), contains("AK-47 | Redline (Field-Tested)"));
    }

    @Test
    @DisplayName("Should skip alert dispatch when price delta < 5.0%")
    void testProcessPriceAlerts_BelowThresholdSkipped() {
        alertService.processPriceAlerts(testItem, BigDecimal.valueOf(10.00), BigDecimal.valueOf(10.30), 3.0);

        verify(userInventoryRepository, never()).findUserIdsByMonitoredItemId(anyLong());
        verify(telegramBotClient, never()).sendPushNotificationAsync(anyLong(), anyString());
    }

    @Test
    @DisplayName("Should format HTML alert message correctly with positive shift emoji")
    void testFormatAlertMessage_PositiveDelta() {
        String htmlMessage = alertService.formatAlertMessage("Gamma Case", BigDecimal.valueOf(1.00), BigDecimal.valueOf(1.10), 10.0);

        assertTrue(htmlMessage.contains("📈"));
        assertTrue(htmlMessage.contains("Gamma Case"));
        assertTrue(htmlMessage.contains("+10.00%"));
        assertTrue(htmlMessage.contains("https://t.me/YourBotName/app"));
    }

    @Test
    @DisplayName("Should format HTML alert message correctly with negative shift emoji")
    void testFormatAlertMessage_NegativeDelta() {
        String htmlMessage = alertService.formatAlertMessage("Gamma Case", BigDecimal.valueOf(1.00), BigDecimal.valueOf(0.90), -10.0);

        assertTrue(htmlMessage.contains("📉"));
        assertTrue(htmlMessage.contains("-10.00%"));
    }
}