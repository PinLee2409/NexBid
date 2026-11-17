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

        // EN: Optional stars for packing, accuracy and speed. / VI: Sao tuỳ chọn cho đóng gói, đúng mô tả và tốc độ.
        @jakarta.validation.constraints.Min(value = 1, message = "Each aspect is 1 to 5 stars")
        @jakarta.validation.constraints.Max(value = 5, message = "Each aspect is 1 to 5 stars")
        Integer packaging,

        @jakarta.validation.constraints.Min(value = 1, message = "Each aspect is 1 to 5 stars")
        @jakarta.validation.constraints.Max(value = 5, message = "Each aspect is 1 to 5 stars")
        Integer accuracy,

        @jakarta.validation.constraints.Min(value = 1, message = "Each aspect is 1 to 5 stars")
        @jakarta.validation.constraints.Max(value = 5, message = "Each aspect is 1 to 5 stars")
        Integer speed,

        @Size(max = 500, message = "The comment must be at most 500 characters")
        String comment) {
}
