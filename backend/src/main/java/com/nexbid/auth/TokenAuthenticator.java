package com.nexbid.auth;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.nexbid.auth.jwt.JwtService;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;

/**
 * EN: Turns an `Authorization: Bearer …` value into the signed-in user — for HTTP requests and for the
 *     realtime socket alike, so both refuse the same tokens and the same blocked accounts.
 * VI: Biến giá trị `Authorization: Bearer …` thành người dùng đã đăng nhập — dùng chung cho request HTTP và
 *     socket realtime, để cả hai từ chối đúng cùng những token và cùng những tài khoản bị khoá.
 */
@Component
public class TokenAuthenticator {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService users;

    TokenAuthenticator(JwtService jwtService, UserService users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    /**
     * EN: Empty for a missing, malformed or expired token, and for an account no longer active.
     * VI: Rỗng khi token thiếu, sai dạng hoặc hết hạn, và khi tài khoản không còn hoạt động.
     */
    public Optional<CurrentUser> authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith(PREFIX)) {
            return Optional.empty();
        }

        return jwtService.read(authorization.substring(PREFIX.length()))
                .filter(user -> users.findById(user.id())
                        .map(account -> account.status() == UserStatus.ACTIVE)
                        .orElse(false));
    }
}
