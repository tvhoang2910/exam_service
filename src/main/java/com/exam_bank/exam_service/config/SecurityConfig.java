package com.exam_bank.exam_service.config;

import com.exam_bank.exam_service.config.properties.AuthJwtProperties;
import com.exam_bank.exam_service.config.properties.CorsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;
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
@EnableConfigurationProperties(AuthJwtProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

        private final AuthJwtProperties authJwtProperties;
        private final CorsProperties corsProperties;

        public SecurityConfig(AuthJwtProperties authJwtProperties, CorsProperties corsProperties) {
                this.authJwtProperties = authJwtProperties;
                this.corsProperties = corsProperties;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, OncePerRequestFilter internalTokenFilter) {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                                                .requestMatchers("/exams/public", "/exams/public/**").permitAll()
                                                .requestMatchers("/sse/**").permitAll()
                                                // internal endpoints require an internal token or valid JWT with INTERNAL role
                                                .requestMatchers("/api/v1/internal/**", "/internal/**").hasRole("INTERNAL")
                                                .requestMatchers(HttpMethod.PUT, "/attempts/*/answers/*/grade")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers("/essay-submissions", "/essay-submissions/**")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers("/attempts", "/attempts/**", "/users/me/attempts")
                                                .hasAnyRole("USER", "ADMIN", "CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.GET, "/tags", "/tags/**").authenticated()
                                                .requestMatchers("/tags").hasAnyRole("ADMIN", "CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.PATCH, "/exams/*/status")
                                                .hasAnyRole("ADMIN", "CONTRIBUTOR")
                                                .requestMatchers("/exams/manage", "/exams/manage/**")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.POST, "/exams", "/exams/upload-source")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.PUT, "/exams/*")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.DELETE, "/exams/*")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers("/exams/**").hasAnyRole("ADMIN", "CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.POST, "/questions")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.GET, "/uploads/pending").hasRole("CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.POST, "/uploads/*/approve", "/uploads/*/reject")
                                                .hasRole("CONTRIBUTOR")
                                                .requestMatchers("/uploads", "/uploads/**")
                                                .hasAnyRole("USER", "CONTRIBUTOR")
                                                .requestMatchers(HttpMethod.PUT, "/admin/reports/questions/*/resolve")
                                                .hasRole("ADMIN")
                                                .requestMatchers("/admin/reports", "/admin/reports/**")
                                                .hasAnyRole("ADMIN", "CONTRIBUTOR")
                                                .requestMatchers("/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/me").hasAnyRole("ADMIN", "USER")
                                                .anyRequest().authenticated())
                                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt
                                                .decoder(jwtDecoder())
                                                .jwtAuthenticationConverter(jwtAuthenticationConverter())));

                // filter that allows service-to-service calls authenticated by X-Internal-Token
                http.addFilterBefore(internalTokenFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public OncePerRequestFilter internalTokenFilter() {
                String expected = System.getenv("NOTIFICATION_INTERNAL_TOKEN");
                return new OncePerRequestFilter() {
                        @Override
                        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                        jakarta.servlet.http.HttpServletResponse response,
                                                        jakarta.servlet.FilterChain filterChain)
                                        throws java.io.IOException, jakarta.servlet.ServletException {
                                String path = request.getRequestURI();
                                if ((path.startsWith("/internal/") || path.startsWith("/api/v1/internal/"))
                                                && expected != null && !expected.isBlank()) {
                                        String header = request.getHeader("X-Internal-Token");
                                        if (expected.equals(header)) {
                                                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                                                "internal", null,
                                                                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
                                                SecurityContextHolder.getContext().setAuthentication(auth);
                                        }
                                }
                                filterChain.doFilter(request, response);
                        }
                };
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
                configuration.setExposedHeaders(List.of("*"));

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
                                .setJwtValidator(new DelegatingOAuth2TokenValidator<>(defaultValidators, hasRoleClaim,
                                                hasUserIdClaim));

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
