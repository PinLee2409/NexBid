package com.nexbid.analytics;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;

/**
 * EN: The admin overview's activity chart. ADMIN comes from the /api/admin/** pattern.
 * VI: Biểu đồ hoạt động của trang tổng quan admin. Quyền ADMIN đến từ mẫu /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {

    private final AnalyticsService analytics;

    public AdminAnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    /** EN: E.g. ?hours=24; at most a week. / VI: Ví dụ ?hours=24; tối đa một tuần. */
    @GetMapping
    public ApiResponse<AnalyticsReport> lastHours(@RequestParam(defaultValue = "24") int hours) {
        return ApiResponse.of(analytics.lastHours(hours, Instant.now()));
    }
}
