package com.nexbid.auction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.nexbid.auction.entity.Auction;
import com.nexbid.auction.repository.AuctionRepository;
import com.nexbid.user.Vacations;

/**
 * EN: Lists a lot again by itself when it closes unsold and the seller asked for it: the same product and terms,
 *     starting the chosen percentage lower, opening at once for as long as the old lot ran. No second review — the
 *     listing was approved and only the price went down. Skipped while the seller is on vacation.
 * VI: Tự đăng lại một lô khi nó đóng mà không bán được và người bán đã yêu cầu: cùng sản phẩm và điều khoản, khởi điểm
 *     thấp hơn theo phần trăm đã chọn, mở ngay và kéo dài bằng lô cũ. Không duyệt lại — nội dung đã được duyệt và chỉ có
 *     giá giảm. Bỏ qua khi người bán đang nghỉ.
 */
@Component
class Relisting {

    private static final Logger log = LoggerFactory.getLogger(Relisting.class);
    private static final BigDecimal ROUND = BigDecimal.valueOf(1_000);

    private final AuctionRepository auctions;
    private final Vacations vacations;
    private final ApplicationEventPublisher events;

    Relisting(AuctionRepository auctions, Vacations vacations, ApplicationEventPublisher events) {
        this.auctions = auctions;
        this.vacations = vacations;
        this.events = events;
    }

    /**
     * EN: Relists {@code ended} if it asked for it; true when a new lot now holds the product.
     * VI: Đăng lại {@code ended} nếu nó đã yêu cầu; true khi đã có lô mới giữ sản phẩm.
     */
    boolean relist(Auction ended, Instant now) {
        if (ended.getRelistLeft() <= 0 || vacations.isAway(ended.getSellerId(), now)) {
            return false;
        }
        // EN: The old lot's close is written first: until then it still holds the product, and the one-live-lot-per-product
        //     index would refuse the new one.
        // VI: Việc đóng lô cũ được ghi trước: trước lúc đó nó vẫn giữ sản phẩm, và index mỗi-sản-phẩm-một-lô-đang-sống sẽ
        //     từ chối lô mới.
        auctions.saveAndFlush(ended);
        // EN: A minute ahead, so the scheduler opens it and everyone hears it start. / VI: Sớm một phút, để bộ lập lịch mở nó và mọi người được báo khi bắt đầu.
        Auction next = copyOf(ended, ended.getRelistDropPercent(), now.plusSeconds(60), AuctionStatus.SCHEDULED);
        next.setRelisting(ended.getRelistLeft() - 1, ended.getRelistDropPercent());
        Auction saved = auctions.saveAndFlush(next);
        log.info("Lot {} closed unsold; relisted as {} from {}", ended.getId(), saved.getId(), saved.getStartingPrice());
        events.publishEvent(new AuctionAuditEvent(
                AuctionAuditEvent.Action.RELISTED, saved.getId(), null, null, saved.getStatus()));
        events.publishEvent(new AuctionRelistedEvent(ended.getId(), saved.getId(), saved.getSellerId(),
                ended.getStartingPrice(), saved.getStartingPrice(), saved.getStartTime()));
        return true;
    }

    /** EN: A copy of {@code old}, {@code dropPercent} cheaper, opening at {@code start}. / VI: Bản chép của {@code old}, rẻ hơn {@code dropPercent}, mở lúc {@code start}. */
    static Auction copyOf(Auction old, int dropPercent, Instant start, AuctionStatus status) {
        Duration length = Duration.between(old.getStartTime(), old.getEndTime());
        if (length.compareTo(Duration.ofHours(1)) < 0) {
            length = Duration.ofHours(1);
        }
        BigDecimal starting = old.getStartingPrice()
                .multiply(BigDecimal.valueOf(100 - dropPercent))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN)
                .divide(ROUND, 0, RoundingMode.DOWN).multiply(ROUND)
                .max(ROUND);
        Auction copy = new Auction(old.getProductId(), old.getSellerId(), starting, old.getMinimumIncrement(),
                start, start.plus(length), old.isAntiSnipingEnabled(), old.getAntiSnipingWindowSeconds(),
                old.getExtensionSeconds());
        copy.setReservePrice(old.getReservePrice());
        // EN: A buy-now price no longer above the start would be meaningless. / VI: Giá mua ngay không còn cao hơn giá khởi điểm thì vô nghĩa.
        copy.setBuyNowPrice(old.getBuyNowPrice() != null && old.getBuyNowPrice().compareTo(starting) > 0
                ? old.getBuyNowPrice() : null);
        copy.setFreeShipping(old.isFreeShipping());
        copy.setRelisting(status == AuctionStatus.DRAFT ? 0 : old.getRelistLeft(), old.getRelistDropPercent());
        copy.setRelistedFrom(old.getId());
        copy.setStatus(status);
        return copy;
    }
}
