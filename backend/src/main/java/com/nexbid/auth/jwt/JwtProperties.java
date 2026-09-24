package com.nexbid.auth.jwt;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Token settings. The secret has a development default but must be supplied by the environment anywhere else.
 * VI: Cấu hình token. Secret có giá trị mặc định cho dev, nhưng ở môi trường khác bắt buộc truyền qua biến môi trường.
 */
@ConfigurationProperties(prefix = "nexbid.jwt")
public record JwtProperties(String secret, Duration expiry, String issuer) {
}
