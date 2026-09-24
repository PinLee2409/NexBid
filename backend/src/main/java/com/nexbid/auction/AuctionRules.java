package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Instant;

import com.nexbid.auction.entity.Auction;

/**
 * EN: The arithmetic behind bidding (spec §8). Kept apart from the service so the rules can be read and
 *     tested on their own, and so bidding at function 20 uses exactly what the detail page displays.
 * VI: Phần tính toán đằng sau việc trả giá (spec §8). Tách khỏi service để luật đọc và kiểm chứng riêng
 *     được, và để việc trả giá ở chức năng 20 dùng đúng con số mà trang chi tiết đang hiển thị.
 */
public final class AuctionRules {

    private AuctionRules() {
    }

    /**
     * EN: What the next bid must at least be. The first bid meets the opening price; after that, each bid
     *     clears the current price by a full increment.
     * VI: Lượt trả giá tiếp theo tối thiểu phải là bao nhiêu. Lượt đầu chỉ cần bằng giá khởi điểm; từ đó
     *     trở đi, mỗi lượt phải vượt giá hiện tại đúng một bước giá.
     */
    public static BigDecimal minimumNextBid(Auction auction) {
        return auction.getBidCount() == 0
                ? auction.getStartingPrice()
                : auction.getCurrentPrice().add(auction.getMinimumIncrement());
    }

    /**
     * EN: Is bidding open right now? Status alone is not enough — a lot can still be marked ACTIVE for the
     *     moment between its clock running out and the scheduler noticing.
     * VI: Ngay lúc này có nhận trả giá không? Chỉ nhìn trạng thái là chưa đủ — một lô vẫn có thể còn được
     *     đánh ACTIVE trong khoảnh khắc giữa lúc hết giờ và lúc scheduler kịp nhận ra.
     */
    public static boolean isOpenForBidding(Auction auction, Instant now) {
        return auction.getStatus() == AuctionStatus.ACTIVE
                && !now.isBefore(auction.getStartTime())
                && now.isBefore(auction.getEndTime());
    }
}
