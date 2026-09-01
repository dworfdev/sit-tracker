package com.sit.tracker.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "id", nullable = false)
    private Long id; // Holds Telegram User ID

    @Column(name = "steam_id", length = 64)
    private String steamId;

    @Column(name = "last_inventory_sync_at")
    private LocalDateTime lastInventorySyncAt;

    @Column(name = "is_premium", nullable = false)
    private Boolean isPremium = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.isPremium == null) {
            this.isPremium = false;
        }
    }
}