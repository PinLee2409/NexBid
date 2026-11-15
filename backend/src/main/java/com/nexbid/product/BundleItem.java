package com.nexbid.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * EN: One piece of a set sold as one lot ("Teacup × 6"). A product with no items is a single item.
 * VI: Một món trong một bộ bán chung một lô ("Tách trà × 6"). Sản phẩm không có món nào là một món đơn lẻ.
 */
public record BundleItem(

        @NotBlank(message = "Each item needs a name")
        @Size(max = 120, message = "Item names must be at most 120 characters")
        String name,

        @NotNull(message = "Each item needs a quantity")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 999, message = "Quantity must be at most 999")
        Integer quantity) {
}
