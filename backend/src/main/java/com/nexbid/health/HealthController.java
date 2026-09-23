package com.nexbid.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe.
 *
 * <p>This answers "is the process serving requests", nothing more. It stays
 * outside the {@code ApiResponse} envelope that every business endpoint uses,
 * because its readers are load balancers and uptime monitors rather than the
 * application — they expect a flat body and a status code, not a wrapper.
 *
 * <p>Whether dependencies are healthy is a different question, and Actuator
 * already answers it at {@code /actuator/health}.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
