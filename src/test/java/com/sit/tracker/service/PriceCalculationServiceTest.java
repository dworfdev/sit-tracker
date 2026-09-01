package com.sit.tracker.service;

import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.repository.PriceHistoryRepository;
import com.sit.tracker.repository.TrackedItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceCalculationServiceTest {

    @Mock
    private TrackedItemRepository trackedItemRepository;

    @Mock
    private PriceHistoryRepository priceHistoryRepository;

    @InjectMocks
    private PriceCalculationService priceCalculationService;

    private TrackedItem mockItem;

    @BeforeEach
    void setUp() {
        mockItem = TrackedItem.builder()
                .id(1L)
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .currentPrice(BigDecimal.valueOf(10.00))
                .build();
    }

    @Test
    @DisplayName("Should correctly calculate positive delta percentage")
    void testCalculatePercentageDelta_PositiveShift() {
        BigDecimal oldPrice = BigDecimal.valueOf(100.00);
        BigDecimal newPrice = BigDecimal.valueOf(110.00);

        Optional<Double> result = priceCalculationService.calculatePercentageDelta(oldPrice, newPrice);

        assertTrue(result.isPresent());
        assertEquals(10.0, result.get());
    }

    @Test
    @DisplayName("Should correctly calculate negative delta percentage")
    void testCalculatePercentageDelta_NegativeShift() {
        BigDecimal oldPrice = BigDecimal.valueOf(100.00);
        BigDecimal newPrice = BigDecimal.valueOf(90.00);

        Optional<Double> result = priceCalculationService.calculatePercentageDelta(oldPrice, newPrice);

        assertTrue(result.isPresent());
        assertEquals(-10.0, result.get());
    }

    @Test
    @DisplayName("Should return empty optional when old price is zero to prevent division by zero")
    void testCalculatePercentageDelta_ZeroDivision() {
        BigDecimal oldPrice = BigDecimal.ZERO;
        BigDecimal newPrice = BigDecimal.valueOf(50.00);

        Optional<Double> result = priceCalculationService.calculatePercentageDelta(oldPrice, newPrice);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty optional if any price parameter is null")
    void testCalculatePercentageDelta_NullHandling() {
        Optional<Double> result1 = priceCalculationService.calculatePercentageDelta(null, BigDecimal.TEN);
        Optional<Double> result2 = priceCalculationService.calculatePercentageDelta(BigDecimal.TEN, null);

        assertTrue(result1.isEmpty());
        assertTrue(result2.isEmpty());
    }

    @Test
    @DisplayName("Should persist price history and identify breach >= 5.0%")
    void testProcessPriceUpdate_BreachThreshold() {
        when(trackedItemRepository.findByMarketHashName("AK-47 | Redline (Field-Tested)"))
                .thenReturn(Optional.of(mockItem));

        BigDecimal updatedPrice = BigDecimal.valueOf(12.00); // 20% increase
        Optional<Double> deltaOpt = priceCalculationService.processPriceUpdate("AK-47 | Redline (Field-Tested)", updatedPrice);

        assertTrue(deltaOpt.isPresent());
        assertEquals(20.0, deltaOpt.get());
        assertTrue(Math.abs(deltaOpt.get()) >= 5.0);

        verify(trackedItemRepository, times(1)).save(mockItem);
        verify(priceHistoryRepository, times(1)).save(any());
    }
}