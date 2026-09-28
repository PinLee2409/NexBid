package com.nexbid.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** EN: Required, so the applicant knows what to change before trying again. / VI: Bắt buộc, để người nộp biết cần sửa gì trước khi gửi lại. */
public record RejectSellerApplicationRequest(

        @NotBlank(message = "A reason is required")
        @Size(max = 500, message = "The reason must be at most 500 characters")
        String reason) {
}
