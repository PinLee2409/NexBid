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
        /** EN: Null when the seller gave no size. / VI: Null khi người bán chưa khai kích thước. */
        Parcel parcel,
        /** EN: The pieces of a set; empty for a single item. / VI: Các món của một bộ; rỗng với món đơn lẻ. */
        java.util.List<BundleItem> items,
        /** EN: A short video, or null. / VI: Một video ngắn, hoặc null. */
        String videoUrl,
        /** EN: The photos are frames of a 360° turn. / VI: Các ảnh là khung hình của một vòng xoay 360°. */
        boolean spinView,
        Instant createdAt,
        Instant updatedAt) {
}
