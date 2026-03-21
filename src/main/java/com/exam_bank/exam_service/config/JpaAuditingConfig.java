package com.exam_bank.exam_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("system");
            }

            if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
                Object userIdClaim = jwtAuthenticationToken.getToken().getClaims().get("userId");
                if (userIdClaim != null) {
                    return Optional.of(String.valueOf(userIdClaim));
                }
            }

            String principal = authentication.getName();
            if (principal == null || principal.isBlank() || "anonymousUser".equalsIgnoreCase(principal)) {
                return Optional.of("system");
            }

            return Optional.of(principal);
        };
    }
}
