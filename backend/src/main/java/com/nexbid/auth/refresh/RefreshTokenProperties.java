package com.nexbid.auth.refresh;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * EN: Refresh token settings: how long a session lives after its last use, whether its cookie is HTTPS-only,
 *     and how often expired tokens are deleted.
 * VI: Cấu hình refresh token: một phiên sống bao lâu sau lần dùng cuối, cookie có chỉ đi qua HTTPS không, và bao
 *     lâu thì xoá các token đã hết hạn một lần.
 */
@ConfigurationProperties(prefix = "nexbid.auth.refresh")
public record RefreshTokenProperties(Duration ttl, boolean secureCookie, Duration pruneInterval) {
}
