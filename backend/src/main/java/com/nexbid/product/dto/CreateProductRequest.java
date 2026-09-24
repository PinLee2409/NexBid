package com.nexbid.product.dto;

import java.util.UUID;

import com.nexbid.product.ProductCondition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: What a seller sends (guide §11). There is no sellerId field — ownership comes from the token,
 *     so one seller cannot file a product under another's name.
 * VI: Những gì người bán gửi lên (guide §11). Không có field sellerId — quyền sở hữu lấy từ token,
 *     nên người bán này không thể đăng sản phẩm dưới tên người bán khác.
 */
public record CreateProductRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 160, message = "Name must be at most 160 characters")
        String name,

        @NotBlank(message = "Description is required")
        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        @NotNull(message = "Category is required")
        UUID categoryId,

        @NotNull(message = "Condition is required")
        ProductCondition condition,

        /**
         * EN: Optional. Left out, the product is a DRAFT — a seller can save a half-written listing.
         * VI: Không bắt buộc. Bỏ trống thì sản phẩm là DRAFT — người bán lưu được bản nháp viết dở.
         */
        Boolean publishNow) {
}
