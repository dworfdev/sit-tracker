package com.sit.tracker.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "user_inventory",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_user_item", columnNames = {"user_id", "item_id"})
        },
        indexes = {
                @Index(name = "idx_user_monitored", columnList = "user_id, is_monitored")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private TrackedItem item;

    @Column(name = "amount", nullable = false)
    private Integer amount = 1;

    @Column(name = "is_monitored", nullable = false)
    private Boolean isMonitored = false;
}