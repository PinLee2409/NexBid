package com.nexbid.auth.refresh;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * EN: Deletes refresh tokens past their expiry: an expired token is refused whether or not its row exists.
 * VI: Xoá các refresh token đã hết hạn: token hết hạn đằng nào cũng bị từ chối, còn dòng hay không cũng vậy.
 */
@Component
@ConditionalOnProperty(name = "nexbid.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class RefreshTokenPruner {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenPruner.class);

    private final RefreshTokenService refreshTokens;

    RefreshTokenPruner(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Scheduled(fixedDelayString = "${nexbid.auth.refresh.prune-interval}")
    void prune() {
        int deleted = refreshTokens.pruneExpired();
        if (deleted > 0) {
            log.debug("Deleted {} expired refresh tokens", deleted);
        }
    }
}
