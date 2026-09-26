package com.nexbid.auction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.nexbid.auction.repository.AuctionRepository;
import com.nexbid.product.ProductImagesChanged;

/**
 * EN: Drops the cached card of every lot showing a product whose photos changed. After commit, so a reader
 *     cannot put the old photos straight back; the TTL covers what is left of that window.
 * VI: Bỏ thẻ cache của mọi lô đang hiển thị sản phẩm vừa đổi ảnh. Làm sau commit, để người đọc không thể nạp
 *     ngay lại ảnh cũ; TTL lo phần còn lại của khoảng hở đó.
 */
@Component
class LotCardEviction {

    private final AuctionRepository auctions;
    private final LotCache cards;

    LotCardEviction(AuctionRepository auctions, LotCache cards) {
        this.auctions = auctions;
        this.cards = cards;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPhotosChanged(ProductImagesChanged event) {
        cards.evict(auctions.findIdsByProductId(event.productId()));
    }
}
