package com.exam_bank.exam_service.service;

import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.springframework.util.StringUtils.hasText;

@Service
public class UserProfileCacheService {

    private final ConcurrentMap<Long, CachedUserProfile> profileByUserId = new ConcurrentHashMap<>();

    public Optional<String> findDisplayName(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        CachedUserProfile profile = profileByUserId.get(userId);
        if (profile == null || !hasText(profile.fullName())) {
            return Optional.empty();
        }

        return Optional.of(profile.fullName());
    }

    public Optional<Boolean> findPremiumStatus(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        CachedUserProfile profile = profileByUserId.get(userId);
        if (profile == null || profile.premium() == null) {
            return Optional.empty();
        }

        return Optional.of(profile.premium());
    }

    public void putDisplayName(Long userId, String fullName) {
        upsert(userId, fullName, null);
    }

    public void putPremiumStatus(Long userId, Boolean premium) {
        upsert(userId, null, premium);
    }

    public void upsert(Long userId, String fullName, Boolean premium) {
        if (userId == null || userId <= 0) {
            return;
        }

        String normalizedFullName = hasText(fullName)
                ? fullName.trim().replaceAll("\\s+", " ")
                : null;

        profileByUserId.compute(userId, (key, existing) -> {
            if (existing == null) {
                return new CachedUserProfile(normalizedFullName, premium);
            }

            String nextFullName = normalizedFullName != null ? normalizedFullName : existing.fullName();
            Boolean nextPremium = premium != null ? premium : existing.premium();
            return new CachedUserProfile(nextFullName, nextPremium);
        });
    }

    private record CachedUserProfile(String fullName, Boolean premium) {
    }
}
