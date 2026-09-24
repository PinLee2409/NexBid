package com.nexbid.support;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.nexbid.auth.JwtAuthenticationFilter;
import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.auth.jwt.JwtService;
import com.nexbid.infrastructure.config.SecurityConfig;

/**
 * EN: The real security chain for web slice tests, so a slice tests the filters the app actually runs.
 * VI: Chuỗi bảo mật thật cho các test lát web, để test đúng những filter ứng dụng thực sự chạy.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
@Import({ SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class })
public class WebSliceSecurity {
}
