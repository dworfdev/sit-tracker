package com.sit.tracker.service;

import com.sit.tracker.entity.TrackedItem;
import com.sit.tracker.entity.User;
import com.sit.tracker.entity.UserInventory;
import com.sit.tracker.repository.TrackedItemRepository;
import com.sit.tracker.repository.UserInventoryRepository;
import com.sit.tracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class DataSeederService {

    private final UserRepository userRepository;
    private final TrackedItemRepository trackedItemRepository;
    private final UserInventoryRepository userInventoryRepository;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedMockData() {
        if (userRepository.count() > 0) {
            log.info("Local database already populated. Skipping seeding execution.");
            return;
        }

        log.info("Populating development environment mock datasets...");

        User testUser = User.builder()
                .id(1L)
                .steamId("76561198000000000")
                .isPremium(true)
                .build();
        userRepository.save(testUser);

        TrackedItem item1 = TrackedItem.builder()
                .marketHashName("AK-47 | Redline (Field-Tested)")
                .iconUrl("https://community.steamstatic.com/economy/image/i0CoZ81Ui0m-9KwlBY1L_18myuGuq1wfhWSaZgMttyVfPaERSR0Wqmu7LAocGIGz3UqlXOLrxM-vMGmW8VNxu5Dx60noTyLwlcK3wiFO0POlPPNSI_-RHGavzedxuPUnFniykEtzsWWBzoyuIiifaAchDZUjTOZe4RC_w4buM-6z7wzbgokUyzK-0H08hRGDMA")
                .currentPrice(BigDecimal.valueOf(14.50))
                .build();

        TrackedItem item2 = TrackedItem.builder()
                .marketHashName("AWP | Asiimov (Field-Tested)")
                .iconUrl("https://community.cloudflare.steamstatic.com/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLVb4a1bwvqHO-je&quality=90")
                .currentPrice(BigDecimal.valueOf(98.20))
                .build();

        TrackedItem item3 = TrackedItem.builder()
                .marketHashName("Clutch Case")
                .iconUrl("https://community.cloudflare.steamstatic.com/economy/image/-9a81dlWLwJ2UUGcVs_nsVtzdOEdtWwKGZZLVb4a1bwvqHO-je&quality=90")
                .currentPrice(BigDecimal.valueOf(0.85))
                .build();

        trackedItemRepository.saveAll(List.of(item1, item2, item3));

        UserInventory inv1 = UserInventory.builder().user(testUser).item(item1).amount(2).isMonitored(true).build();
        UserInventory inv2 = UserInventory.builder().user(testUser).item(item2).amount(1).isMonitored(true).build();
        UserInventory inv3 = UserInventory.builder().user(testUser).item(item3).amount(50).isMonitored(false).build();

        userInventoryRepository.saveAll(List.of(inv1, inv2, inv3));
        log.info("Development mock environment successfully initialized.");
    }
}