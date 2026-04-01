package com.exam_bank.exam_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@RequestMapping
@Slf4j
public class ExamAccessController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        log.debug("me: username={}", authentication.getName());
        return ResponseEntity.ok(Map.of(
                "authenticated", true,
                "username", authentication.getName(),
                "authorities", authentication.getAuthorities()));
    }

    @GetMapping("/admin/ping")
    public ResponseEntity<Map<String, String>> adminOnly() {
        log.info("admin/ping: ADMIN access granted");
        return ResponseEntity.ok(Map.of("message", "ADMIN access granted"));
    }
}
