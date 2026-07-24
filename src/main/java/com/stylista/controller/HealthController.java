package com.stylista.controller;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of("service", "stylista-backend", "status", "ok");
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP");
    }

    // NEW: DB-touching keep-alive — wakes Render AND keeps Supabase from pausing
    @GetMapping("/api/health")
    public Map<String, Object> apiHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        body.put("service", "stylista-backend");
        body.put("timestamp", Instant.now().toString());
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            body.put("database", "up");
        } catch (Exception e) {
            body.put("database", "down");
        }
        return body;
    }
}