package com.insightagent.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/health")
@Tag(name = "Health", description = "Liveness probe")
public class HealthController {

    @GetMapping
    @Operation(summary = "Liveness check")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "insight-agent",
                "version", "0.0.1",
                "timestamp", Instant.now().toString()
        );
    }
}
