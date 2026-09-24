package com.nexbid.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: What an admin may set. Slug is optional — left out, it is derived from the name.
 * VI: Những gì admin được đặt. Slug không bắt buộc — bỏ trống thì sinh tự động từ tên.
 */
public record SaveCategoryRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 80, message = "Name must be at most 80 characters")
        String name,

        @Size(max = 80, message = "Slug must be at most 80 characters")
        String slug,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @Size(max = 500, message = "Image URL must be at most 500 characters")
        String imageUrl) {
}
