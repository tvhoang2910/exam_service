package com.exam_bank.exam_service.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class JpaAuditingConfigTest {

    private final JpaAuditingConfig config = new JpaAuditingConfig();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditorProvider_shouldReturnUserIdClaim_whenJwtContainsUserId() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("john@example.com")
                .claim("userId", 7L)
                .claim("role", "USER")
                .build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES,
                jwt.getSubject());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        AuditorAware<String> auditorAware = config.auditorProvider();
        assertThat(auditorAware.getCurrentAuditor()).contains("7");
    }

    @Test
    void auditorProvider_shouldReturnSystem_whenAnonymousOrMissingAuthentication() {
        AuditorAware<String> auditorAware = config.auditorProvider();
        assertThat(auditorAware.getCurrentAuditor()).contains("system");

        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        SecurityContextHolder.getContext().setAuthentication(anonymous);

        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }
}
