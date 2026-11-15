package com.nexbid.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * EN: The packed item, for shipping quotes: weight in grams, sides in centimetres. Optional on a product; quotes use
 *     DEFAULT when the seller left it out.
 * VI: Kiện hàng đã đóng gói, để báo phí ship: cân nặng tính bằng gam, các cạnh tính bằng cm. Không bắt buộc trên sản
 *     phẩm; báo phí dùng DEFAULT khi người bán bỏ trống.
 */
public record Parcel(

        @NotNull(message = "Weight is required")
        @Min(value = 1, message = "Weight must be at least 1 gram")
        @Max(value = 30000, message = "Weight must be at most 30 kg")
        Integer weightGrams,

        @NotNull(message = "Length is required")
        @Min(value = 1, message = "Length must be at least 1 cm")
        @Max(value = 150, message = "Length must be at most 150 cm")
        Integer lengthCm,

        @NotNull(message = "Width is required")
        @Min(value = 1, message = "Width must be at least 1 cm")
        @Max(value = 150, message = "Width must be at most 150 cm")
        Integer widthCm,

        @NotNull(message = "Height is required")
        @Min(value = 1, message = "Height must be at least 1 cm")
        @Max(value = 150, message = "Height must be at most 150 cm")
        Integer heightCm) {

    /** EN: 1 kg in a 20×15×10 cm box. / VI: 1 kg trong hộp 20×15×10 cm. */
    public static final Parcel DEFAULT = new Parcel(1000, 20, 15, 10);
}
