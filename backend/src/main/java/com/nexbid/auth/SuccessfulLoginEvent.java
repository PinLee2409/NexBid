package com.nexbid.auth;

import java.util.UUID;

/** EN: Credentials were accepted and a token was issued. / VI: Thông tin đăng nhập hợp lệ và token đã được cấp. */
public record SuccessfulLoginEvent(UUID userId) {
}
