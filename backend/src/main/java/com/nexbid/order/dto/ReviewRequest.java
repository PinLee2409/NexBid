package com.nexbid.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** EN: One to five stars; the comment is optional. / VI: Một tới năm sao; nhận xét là tuỳ chọn. */
public record ReviewRequest(

        @NotNull(message = "A rating is required")
        @Min(value = 1, message = "The rating must be 1 to 5 stars")
        @Max(value = 5, message = "The rating must be 1 to 5 stars")
        Integer rating,

        @Size(max = 500, message = "The comment must be at most 500 characters")
        String comment) {
}
