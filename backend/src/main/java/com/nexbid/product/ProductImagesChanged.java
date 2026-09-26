package com.nexbid.product;

import java.util.UUID;

/**
 * EN: A product's photos were added, removed or reordered. Raised in the change's transaction.
 * VI: Ảnh của một sản phẩm vừa được thêm, xoá hoặc đổi thứ tự. Phát ra trong transaction của thay đổi đó.
 */
public record ProductImagesChanged(UUID productId) {
}
