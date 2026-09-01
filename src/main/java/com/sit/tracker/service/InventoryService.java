package com.sit.tracker.service;

import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.entity.User;
import com.sit.tracker.entity.UserInventory;
import com.sit.tracker.repository.TrackedItemRepository;
import com.sit.tracker.repository.UserInventoryRepository;
import com.sit.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InventoryService {

    // Server-side cooldown floor: enforced regardless of how many devices,
    // tabs, or direct API calls a user makes — the client-side cooldown in
    // app.js is only a UX nicety and is trivially bypassable.
    private static final Duration SYNC_COOLDOWN = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final UserInventoryRepository userInventoryRepository;

    @Transactional
    public void linkSteamId(Long userId, String steamId) {
        User user = userRepository.findById(userId)
                .orElseGet(() -> userRepository.save(User.builder().id(userId).isPremium(false).build()));
        user.setSteamId(steamId);
        userRepository.save(user);
    }

    /**
     * Throws IllegalStateException if the user is still within their sync
     * cooldown window. Callers should check this before hitting Steam.
     */
    public void assertSyncAllowed(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getLastInventorySyncAt() != null) {
            Duration elapsed = Duration.between(user.getLastInventorySyncAt(), LocalDateTime.now());
            if (elapsed.compareTo(SYNC_COOLDOWN) < 0) {
                long secondsLeft = SYNC_COOLDOWN.minus(elapsed).getSeconds();
                throw new IllegalStateException(
                        "Please wait " + secondsLeft + "s before syncing your inventory again.");
            }
        }
    }

    @Transactional
    public void processAndSyncInventory(Long userId, List<String> itemNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        for (String itemName : itemNames) {
            TrackedItem item = trackedItemRepository.findByMarketHashName(itemName)
                    .orElseGet(() -> trackedItemRepository.save(
                            TrackedItem.builder()
                                    .marketHashName(itemName)
                                    .currentPrice(BigDecimal.ZERO)
                                    .build()
                    ));

            userInventoryRepository.findByUserIdAndItemId(user.getId(), item.getId())
                    .ifPresentOrElse(
                            inv -> inv.setAmount(inv.getAmount() + 1),
                            () -> userInventoryRepository.save(
                                    UserInventory.builder()
                                            .user(user)
                                            .item(item)
                                            .amount(1)
                                            .isMonitored(false)
                                            .build()
                            )
                    );
        }

        user.setLastInventorySyncAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<UserInventory> getUserDashboard(Long userId) {
        return userInventoryRepository.findByUserId(userId);
    }

    @Transactional
    public boolean toggleItemMonitoring(Long userId, Long itemId, boolean enable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (enable && !Boolean.TRUE.equals(user.getIsPremium())) {
            long currentMonitoredCount = userInventoryRepository.countByUserIdAndIsMonitoredTrue(userId);
            if (currentMonitoredCount >= 3) {
                throw new IllegalStateException("Freemium tier limit reached: Maximum 3 monitored items allowed.");
            }
        }

        UserInventory inventory = userInventoryRepository.findByUserIdAndItemId(userId, itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found in user inventory"));

        inventory.setIsMonitored(enable);
        userInventoryRepository.save(inventory);
        return enable;
    }
}