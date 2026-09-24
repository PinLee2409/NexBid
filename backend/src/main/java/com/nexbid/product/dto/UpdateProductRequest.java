package com.nexbid.product.dto;

import java.util.UUID;

import com.nexbid.product.ProductCondition;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: What a seller may change (guide §13). No sellerId and no status: ownership comes from the token, and
 *     IN_AUCTION / SOLD are set by the auction flow, never typed in by hand.
 * VI: Những gì người bán được sửa (guide §13). Không có sellerId và không có status: chủ sở hữu lấy từ token,
 *     còn IN_AUCTION / SOLD do luồng đấu giá đặt chứ không ai gõ tay.
 */
public record UpdateProductRequest(

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
         * EN: Moves between DRAFT and AVAILABLE only — the two states a seller owns.
         * VI: Chỉ chuyển giữa DRAFT và AVAILABLE — hai trạng thái thuộc quyền người bán.
         */
        Boolean published) {
}
