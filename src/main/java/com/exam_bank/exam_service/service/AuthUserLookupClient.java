package com.exam_bank.exam_service.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.Optional;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthUserLookupClient {

    private final UserProfileCacheService userProfileCacheService;

    public Optional<String> findDisplayNameByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        return userProfileCacheService.findDisplayName(userId)
                .filter(value -> hasText(value))
                .map(value -> value.trim().replaceAll("\\s+", " "));
    }

    public Optional<Boolean> findPremiumStatusByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        return userProfileCacheService.findPremiumStatus(userId);
    }
}
