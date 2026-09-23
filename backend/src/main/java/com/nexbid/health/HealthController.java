package com.nexbid.health;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * EN: Liveness probe — is the process serving? Flat body, outside the ApiResponse envelope, for load balancers.
 * VI: Probe sống — tiến trình còn phục vụ không? Body phẳng, nằm ngoài khung ApiResponse, dành cho load balancer.
 *
 * <p>EN: Whether the database is healthy is a different question, answered at /actuator/health.
 * <p>VI: Database c    òn khoẻ hay không là câu hỏi khác, /actuator/health trả lời.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
