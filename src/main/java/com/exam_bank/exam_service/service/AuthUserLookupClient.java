package com.exam_bank.exam_service.service;

import static org.springframework.util.StringUtils.hasText;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthUserLookupClient {

    private final RestTemplate restTemplate;

    @Value("${auth.service.base-url:http://localhost:8080}")
    private String authServiceBaseUrl;

    @Value("${auth.service.internal-token:change-me-secret}")
    private String internalToken;

    public Optional<String> findDisplayNameByUserId(Long userId) {
        if (userId == null || userId <= 0) {
            return Optional.empty();
        }

        String url = authServiceBaseUrl + "/api/v1/auth/internal/users/{userId}/display-name";
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);

        try {
            ResponseEntity<UserDisplayNameResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    UserDisplayNameResponse.class,
                    userId);

            UserDisplayNameResponse body = response.getBody();
            if (body == null || !hasText(body.fullName())) {
                return Optional.empty();
            }

            return Optional.of(body.fullName().trim().replaceAll("\\s+", " "));
        } catch (Exception exception) {
            log.warn("Failed to resolve display name for userId {}: {}", userId, exception.getMessage());
            return Optional.empty();
        }
    }

    private record UserDisplayNameResponse(Long userId, String fullName) {
    }
}
