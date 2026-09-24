package com.nexbid.auction;

import java.util.List;

import com.nexbid.product.ProductImageView;
import com.nexbid.product.ProductView;

/**
 * EN: Everything an admin needs to judge a lot (guide §16): the terms, the item, its photos and who is
 *     selling it. Assembled here so the reviewer decides from one response, not four.
 * VI: Mọi thứ admin cần để phán xét một lô (guide §16): điều khoản, món hàng, ảnh của nó, và ai đang bán.
 *     Gom lại ở đây để người duyệt quyết định từ một response, không phải bốn.
 */
public record AuctionDetailView(
        AuctionView auction,
        ProductView product,
        List<ProductImageView> images,
        Seller seller) {

    public record Seller(java.util.UUID id, String fullName) {
    }
}
