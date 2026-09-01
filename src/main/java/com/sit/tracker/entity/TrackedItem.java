package com.sit.tracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "tracked_items", indexes = {
        @Index(name = "idx_market_hash_name", columnList = "market_hash_name", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "market_hash_name", nullable = false, unique = true, length = 255)
    private String marketHashName;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "current_price", precision = 10, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    @PrePersist
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}