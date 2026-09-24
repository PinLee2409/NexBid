package com.nexbid.product;

import java.util.UUID;

/**
 * EN: What other modules and the API see of a category. A view, not the entity.
 * VI: Những gì module khác và API nhìn thấy về một danh mục. Là bản xem, không phải entity.
 */
public record CategoryView(
        UUID id,
        String name,
        String slug,
        String description,
        String imageUrl,
        CategoryStatus status) {
}
