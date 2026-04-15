package com.exam_bank.exam_service.config;

import com.exam_bank.exam_service.config.properties.AuthJwtProperties;
import com.exam_bank.exam_service.config.properties.CorsProperties;
import com.exam_bank.exam_service.config.properties.MinioProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.List;

@Configuration
@EnableConfigurationProperties({AuthJwtProperties.class, MinioProperties.class})
public class SecurityConfig {

    private final AuthJwtProperties authJwtProperties;
    private final CorsProperties corsProperties;

    public SecurityConfig(AuthJwtProperties authJwtProperties, CorsProperties corsProperties) {
        this.authJwtProperties = authJwtProperties;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/exams/public", "/exams/public/**").permitAll()
                        .requestMatchers("/sse/**").permitAll()
                        .requestMatchers("/attempts", "/attempts/**", "/users/me/attempts")
                        .hasAnyRole("USER", "ADMIN", "CONTRIBUTOR")
                        .requestMatchers("/tags", "/tags/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                        .requestMatchers("/exams/manage", "/exams/manage/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                        .requestMatchers("/exams/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                        .requestMatchers("/admin/reports", "/admin/reports/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/me").hasAnyRole("ADMIN", "USER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(corsProperties.getAllowedOrigins().split(","))
                .stream()
                .map(String::trim)
                .toList());
        configuration.setAllowedMethods(corsProperties.getAllowedMethods());
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());
        configuration.setAllowCredentials(corsProperties.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        SecretKey secretKey = getJwtSecretKey();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> defaultValidators = JwtValidators
                .createDefaultWithIssuer(authJwtProperties.getIssuer());
        OAuth2TokenValidator<Jwt> hasRoleClaim = new JwtClaimValidator<>("role",
                role -> role instanceof String value && StringUtils.hasText(value));
        OAuth2TokenValidator<Jwt> hasUserIdClaim = new JwtClaimValidator<>("userId", claim -> {
            if (claim instanceof Number number) {
                return number.longValue() > 0;
            }
            if (claim instanceof String value) {
                try {
                    return Long.parseLong(value.trim()) > 0;
                } catch (NumberFormatException ex) {
                    return false;
                }
            }
            return false;
        });
        jwtDecoder
                .setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, hasRoleClaim, hasUserIdClaim));

        return jwtDecoder;
    }

    @Bean
    public Converter<Jwt, JwtAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            String role = jwt.getClaimAsString("role");
            Collection<SimpleGrantedAuthority> authorities = StringUtils.hasText(role)
                    ? List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    : List.of();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }

    private SecretKey getJwtSecretKey() {
        String secretBase64 = authJwtProperties.getSecret();
        if (!StringUtils.hasText(secretBase64)) {
            throw new IllegalStateException("auth.jwt.secret must not be empty");
        }

        byte[] keyBytes = Base64.getDecoder().decode(secretBase64);
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}