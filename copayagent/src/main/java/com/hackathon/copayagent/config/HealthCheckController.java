package com.hackathon.copayagent.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheckController {
    
    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", "Agentico de Copago y Cobertura");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> detailedHealth() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", "Agentico de Copago y Cobertura");
        response.put("timestamp", System.currentTimeMillis());
        response.put("endpoints", Map.of(
            "chat", "/api/chat",
            "health", "/health"
        ));
        return ResponseEntity.ok(response);
    }
}
