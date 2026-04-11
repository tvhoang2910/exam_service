package com.exam_bank.exam_service.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthenticatedUserService {

    public Optional<Long> getCurrentUserIdOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }

        return Optional.ofNullable(extractUserIdFromClaims(jwtAuth));
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }

        Long userId = extractUserIdFromClaims(jwtAuth);
        if (userId == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing userId claim");
        }
        return userId;
    }

    private Long extractUserIdFromClaims(JwtAuthenticationToken jwtAuth) {
        Object userIdClaim = jwtAuth.getToken().getClaims().get("userId");
        if (userIdClaim == null) {
            return null;
        }

        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(userIdClaim));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
