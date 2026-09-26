package com.nexbid.user.dto;

import jakarta.validation.constraints.NotNull;

/** EN: True blocks an account; false restores access. / VI: True chặn tài khoản; false mở lại quyền truy cập. */
public record SetBlockedRequest(@NotNull Boolean blocked) {
}
