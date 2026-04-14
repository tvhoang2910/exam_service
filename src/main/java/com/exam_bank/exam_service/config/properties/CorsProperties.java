package com.exam_bank.exam_service.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.cors")
public class CorsProperties {

 @NotBlank
 private String allowedOrigins;

 private List<String> allowedMethods = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

 private List<String> allowedHeaders = List.of("Authorization", "Content-Type", "Accept");

 private List<String> exposedHeaders = List.of("Authorization");

 private boolean allowCredentials = true;
}
