package com.nexbid.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexbid.auth.jwt.JwtService;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;
import com.nexbid.user.UserStatusChangedEvent;

/**
 * EN: Turns an `Authorization: Bearer …` value into the signed-in user — for HTTP requests and for the
 *     realtime socket alike, so both refuse the same tokens and the same blocked accounts.
 * VI: Biến giá trị `Authorization: Bearer …` thành người dùng đã đăng nhập — dùng chung cho request HTTP và
 *     socket realtime, để cả hai từ chối đúng cùng những token và cùng những tài khoản bị khoá.
 */
@Component
public class TokenAuthenticator {

    private static final String PREFIX = "Bearer ";

    // EN: A bound on memory, not on correctness: past it the cache simply starts over.
    // VI: Giới hạn bộ nhớ, không phải giới hạn tính đúng: vượt quá thì cache chỉ việc làm lại từ đầu.
    private static final int MAX_CACHED = 10_000;

    private final JwtService jwtService;
    private final UserService users;
    private final Duration statusTtl;

    /** EN: Whether each account was active, and until when that answer may be reused. / VI: Mỗi tài khoản có đang hoạt động không, và câu trả lời đó được dùng lại tới khi nào. */
    private final Map<UUID, CachedStatus> statuses = new ConcurrentHashMap<>();

    private record CachedStatus(boolean active, Instant until) {
    }

    TokenAuthenticator(
            JwtService jwtService,
            UserService users,
            @Value("${nexbid.auth.status-cache-ttl}") Duration statusTtl) {

        this.jwtService = jwtService;
        this.users = users;
        this.statusTtl = statusTtl;
    }

    /**
     * EN: Empty for a missing, malformed or expired token, and for an account no longer active.
     * VI: Rỗng khi token thiếu, sai dạng hoặc hết hạn, và khi tài khoản không còn hoạt động.
     */
    public Optional<CurrentUser> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return Optional.empty();
        }

        return jwtService.read(authorization.substring(PREFIX.length())).filter(user -> isActive(user.id()));
    }

    /**
     * EN: The load test showed this lookup on every request holding database connections at 1000 users
     *     (docs/load-test). The answer is reused for a few seconds; a block or unblock drops it at once.
     * VI: Load test cho thấy việc tra cứu này ở mọi request giữ chặt connection database khi có 1000 user
     *     (docs/load-test). Câu trả lời được dùng lại vài giây; khoá hay mở khoá thì bỏ nó ngay lập tức.
     */
    private boolean isActive(UUID userId) {
        Instant now = Instant.now();
        CachedStatus cached = statuses.get(userId);
        if (cached != null && now.isBefore(cached.until())) {
            return cached.active();
        }

        boolean active = users.findById(userId).map(account -> account.status() == UserStatus.ACTIVE).orElse(false);
        if (statuses.size() >= MAX_CACHED) {
            statuses.clear();
        }
        statuses.put(userId, new CachedStatus(active, now.plus(statusTtl)));
        return active;
    }

    /**
     * EN: After commit, so the next request reads the new status rather than the one being replaced.
     * VI: Sau khi commit, để request kế tiếp đọc trạng thái mới chứ không phải trạng thái đang bị thay.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStatusChanged(UserStatusChangedEvent event) {
        statuses.remove(event.userId());
    }
}
