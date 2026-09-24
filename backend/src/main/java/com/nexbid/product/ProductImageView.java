package com.nexbid.product;

import java.util.UUID;

/** EN: An image as the API returns it. / VI: Một tấm ảnh dưới dạng API trả về. */
public record ProductImageView(UUID id, String url, String alt, int sortOrder) {
}
