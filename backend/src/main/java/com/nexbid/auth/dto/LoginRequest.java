package com.nexbid.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * EN: Login input. Deliberately no format rules — a wrong email should fail as bad credentials, not as validation.
 * VI: Dữ liệu đăng nhập. Cố ý không ràng buộc định dạng — email sai phải trả về sai thông tin đăng nhập, không phải lỗi validation.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Password is required") String password) {
}
