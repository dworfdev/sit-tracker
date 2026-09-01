package com.sit.tracker.repository;

import com.sit.tracker.entity.UserInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserInventoryRepository extends JpaRepository<UserInventory, Long> {
    List<UserInventory> findByUserId(Long userId);
    Optional<UserInventory> findByUserIdAndItemId(Long userId, Long itemId);
    long countByUserIdAndIsMonitoredTrue(Long userId);

    /**
     * Retrieves all Telegram user IDs actively monitoring a specific item where threshold breaches occur.
     */
    @Query("SELECT ui.user.id FROM UserInventory ui WHERE ui.item.id = :itemId AND ui.isMonitored = true")
    List<Long> findUserIdsByMonitoredItemId(@Param("itemId") Long itemId);
}