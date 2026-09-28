package com.nexbid.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** EN: What the applicant plans to sell — the admin decides on it. / VI: Người nộp định bán gì — admin dựa vào đó để quyết định. */
public record SellerApplicationRequest(

        @NotBlank(message = "Tell us what you plan to sell")
        @Size(max = 1000, message = "The note must be at most 1000 characters")
        String note) {
}
