package com.nexbid.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * EN: Turns on @Scheduled. Kept on its own so the one switch that starts background work is easy to find.
 * VI: Bật @Scheduled. Tách riêng để dễ tìm ra công tắc duy nhất khởi động các tác vụ chạy nền.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
