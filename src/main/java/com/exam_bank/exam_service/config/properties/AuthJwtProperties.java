package com.exam_bank.exam_service.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    @NotBlank
    private String issuer;

    @NotBlank
    private String secret;
}
