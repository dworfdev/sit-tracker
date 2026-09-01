package com.sit.tracker.repository;

import com.sit.tracker.entity.TrackedItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrackedItemRepository extends JpaRepository<TrackedItem, Long> {

    Optional<TrackedItem> findByMarketHashName(String marketHashName);

    /**
     * Retrieves a distinct list of market hash names for items actively monitored by users.
     * Prevents duplicate external API calls during automated background updates.
     */
    @Query("SELECT DISTINCT t.marketHashName FROM UserInventory ui JOIN ui.item t WHERE ui.isMonitored = true")
    List<String> findDistinctMonitoredItemNames();
}