package com.nexbid.watchlist;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.watchlist.entity.Watch;
import com.nexbid.watchlist.repository.WatchRepository;

/**
 * EN: Watching lots (guide §28, spec §15). Both directions are idempotent — tapping twice leaves one
 *     row, untapping twice leaves none — so a double click is never an error.
 * VI: Theo dõi lô (guide §28, spec §15). Cả hai chiều đều idempotent — bấm hai lần vẫn một dòng, bỏ hai
 *     lần vẫn không còn dòng nào — nên bấm đúp không bao giờ thành lỗi.
 */
@Service
public class WatchlistService {

    private final WatchRepository watches;
    private final AuctionService auctions;

    public WatchlistService(WatchRepository watches, AuctionService auctions) {
        this.watches = watches;
        this.auctions = auctions;
    }

    @Transactional
    public WatchState watch(UUID userId, UUID auctionId) {
        // EN: The same door as the lot page: a lot you cannot see, you cannot watch.
        // VI: Cùng một cánh cửa với trang lô: lô không xem được thì cũng không theo dõi được.
        auctions.requirePubliclyVisible(auctionId);

        watches.watch(userId, auctionId);
        return new WatchState(auctionId, true);
    }

    /**
     * EN: No visibility check on the way out. Answering the same for a real lot and a made-up id tells a
     *     stranger nothing, and a lot hidden after it was watched can still be let go of.
     * VI: Bỏ theo dõi thì không kiểm quyền xem. Trả lời như nhau cho lô thật và id bịa không tiết lộ gì cho
     *     người lạ, và lô bị ẩn sau khi đã theo dõi vẫn bỏ theo dõi được.
     */
    @Transactional
    public WatchState unwatch(UUID userId, UUID auctionId) {
        watches.unwatch(userId, auctionId);
        return new WatchState(auctionId, false);
    }

    /**
     * EN: The caller's watchlist, most recently added first. Lots that have since left public view drop
     *     out of it rather than showing up as a card nobody can open.
     * VI: Danh sách theo dõi của người gọi, lô thêm gần nhất đứng đầu. Lô nào đã rời khỏi chế độ công khai
     *     thì rơi khỏi danh sách, thay vì hiện ra như một thẻ không ai mở được.
     */
    /**
     * EN: Everyone watching a lot, for the heads-up notices spec §15 describes.
     * VI: Mọi người đang theo dõi một lô, cho các thông báo nhắc trước mà spec §15 mô tả.
     */
    public List<UUID> watchersOf(UUID auctionId) {
        return watches.findUserIdsByAuctionId(auctionId);
    }

    public List<AuctionSummaryView> watchlistOf(UUID userId) {
        List<UUID> ids = watches.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(Watch::getAuctionId)
                .toList();

        return auctions.publicSummariesOf(ids);
    }
}
