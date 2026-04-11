package com.exam_bank.exam_service.listener;

import com.exam_bank.exam_service.dto.message.UserProfileSyncMessage;
import com.exam_bank.exam_service.service.UserProfileCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserProfileSyncListener {

    private final UserProfileCacheService userProfileCacheService;

    @RabbitListener(queues = "${auth.events.user-profile-sync.queue:exam.auth.user-profile-sync.queue}")
    public void onUserProfileSync(UserProfileSyncMessage message) {
        if (message == null || message.getUserId() == null || message.getUserId() <= 0) {
            log.warn("Received invalid UserProfileSyncMessage, skipping");
            return;
        }

        userProfileCacheService.upsert(message.getUserId(), message.getFullName(), message.getPremium());
        log.debug("Synced user profile cache for userId={} premium={}", message.getUserId(), message.getPremium());
    }
}
