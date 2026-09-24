package com.nexbid.auction;

import java.math.BigDecimal;
import java.time.Duration;
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

    /**
     * EN: Does a bid accepted at {@code now} land inside the anti-sniping window (spec §13)? Inclusive:
     *     with a 30-second window, a bid 30 seconds before the close counts.
     * VI: Lượt trả giá được nhận lúc {@code now} có rơi vào khung chống bid phút chót không (spec §13)?
     *     Tính cả biên: khung 30 giây thì lượt trả giá đúng 30 giây trước giờ đóng vẫn được tính.
     */
    public static boolean isLastMinuteBid(Auction auction, Instant now) {
        if (!auction.isAntiSnipingEnabled()) {
            return false;
        }

        Duration remaining = Duration.between(now, auction.getEndTime());
        return remaining.compareTo(Duration.ofSeconds(auction.getAntiSnipingWindowSeconds())) <= 0;
    }
}
