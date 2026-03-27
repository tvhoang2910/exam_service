package com.exam_bank.exam_service.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthenticatedUserService {

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }

        Object userIdClaim = jwtAuth.getToken().getClaims().get("userId");
        if (userIdClaim == null) {
            throw new ResponseStatusException(UNAUTHORIZED, "Missing userId claim");
        }

        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }

        try {
            return Long.parseLong(String.valueOf(userIdClaim));
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid userId claim");
        }
    }
}
