package com.nexbid.product;

import java.time.Instant;
import java.util.UUID;

/**
 * EN: What the API returns for a product. Carries sellerId so the caller can see whose it is.
 * VI: Những gì API trả về cho một sản phẩm. Có kèm sellerId để bên gọi biết nó của ai.
 */
public record ProductView(
        UUID id,
        UUID sellerId,
        CategoryView category,
        String name,
        String description,
        ProductCondition condition,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {
}
