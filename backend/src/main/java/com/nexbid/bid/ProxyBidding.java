package com.nexbid.bid;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.BiddingState;
import com.nexbid.bid.entity.AutoBid;
import com.nexbid.bid.repository.AutoBidRepository;

/**
 * EN: Lets auto bids answer until the lot settles (guide §31, spec §14), inside the bid's own lock.
 * VI: Để các auto bid đáp trả cho tới khi lô ổn định (guide §31, spec §14), ngay trong khoá của lượt trả giá.
 */
@Component
class ProxyBidding {

    private final AutoBidRepository autoBids;
    private final AuctionService auctions;
    private final BidRecorder recorder;
    private final ApplicationEventPublisher events;

    ProxyBidding(
            AutoBidRepository autoBids,
            AuctionService auctions,
            BidRecorder recorder,
            ApplicationEventPublisher events) {

        this.autoBids = autoBids;
        this.auctions = auctions;
        this.recorder = recorder;
        this.events = events;
    }

    /**
     * EN: Settles the lot, then tells whoever led at some point and no longer does. Returns where it stopped.
     * VI: Làm lô ổn định, rồi báo cho ai từng dẫn mà giờ không còn dẫn. Trả về lô dừng ở đâu.
     */
    BiddingState settleAndAnnounce(UUID auctionId, Instant at, LeadTracker leads) {
        settle(auctionId, at, leads);

        BiddingState lot = auctions.lockBiddingState(auctionId, at);
        Set<UUID> outbid = leads.outbidBy(lot.leaderId());
        if (!outbid.isEmpty()) {
            events.publishEvent(new OutbidEvent(auctionId, outbid, lot.currentPrice(), at));
        }
        return lot;
    }

    /**
     * EN: Each round, the strongest auto bid that can still pay takes on the leader. The loser can never
     *     afford a later round, so rounds are bounded by the number of auto bids.
     * VI: Mỗi vòng, auto bid mạnh nhất còn đủ sức trả sẽ đấu với người dẫn. Bên thua không bao giờ đủ sức
     *     quay lại, nên số vòng không vượt quá số auto bid.
     */
    private void settle(UUID auctionId, Instant at, LeadTracker leads) {
        List<AutoBid> proxies = autoBids.findByAuctionIdAndActiveTrueOrderByMaxAmountDescCreatedAtAsc(auctionId);

        for (int round = 0; round <= proxies.size(); round++) {
            BiddingState lot = auctions.lockBiddingState(auctionId, at);
            if (!lot.open()) {
                return;
            }

            AutoBid challenger = proxies.stream()
                    .filter(proxy -> !proxy.getUserId().equals(lot.leaderId()))
                    .filter(proxy -> proxy.getMaxAmount().compareTo(lot.minimumNextBid()) >= 0)
                    .findFirst()
                    .orElse(null);

            if (challenger == null) {
                return;
            }

            AutoBid defender = proxies.stream()
                    .filter(proxy -> proxy.getUserId().equals(lot.leaderId()))
                    .findFirst()
                    .orElse(null);

            exchange(lot, challenger, defender, at, leads);
        }
    }

    /**
     * EN: Worked out, not played out: step j costs first + (j-1)·increment, challenger odd, defender even.
     *     Only the last two steps are recorded — a tiny increment would otherwise mean thousands of rows.
     * VI: Tính ra chứ không chạy từng bước: bước j giá first + (j-1)·bước giá, thách đấu đi lẻ, giữ vị trí đi
     *     chẵn. Chỉ ghi hai bước cuối — bước giá nhỏ mà chạy đủ thì thành hàng nghìn dòng.
     */
    private void exchange(BiddingState lot, AutoBid challenger, AutoBid defender, Instant at, LeadTracker leads) {
        BigDecimal first = lot.minimumNextBid();
        BigDecimal increment = lot.minimumIncrement();

        long challengerLast = lastAffordable(challenger.getMaxAmount(), first, increment, true);
        long defenderLast = defender == null ? 0 : lastAffordable(defender.getMaxAmount(), first, increment, false);
        long steps = Math.min(challengerLast, defenderLast) + 1;

        boolean challengerWins = steps % 2 == 1;
        UUID winner = challengerWins ? challenger.getUserId() : defender.getUserId();

        if (steps > 1) {
            UUID loser = challengerWins ? defender.getUserId() : challenger.getUserId();
            recorder.record(lot.auctionId(), loser, amountOf(steps - 1, first, increment), at, leads);
        }

        recorder.record(lot.auctionId(), winner, amountOf(steps, first, increment), at, leads);
    }

    /**
     * EN: The last odd (challenger) or even (defender) step this ceiling can pay for; zero if none.
     * VI: Bước lẻ (thách đấu) hoặc chẵn (giữ vị trí) cuối cùng mà mức trần này trả nổi; 0 nếu không có.
     */
    static long lastAffordable(BigDecimal ceiling, BigDecimal first, BigDecimal increment, boolean oddSteps) {
        if (ceiling.compareTo(first) < 0) {
            return 0;
        }

        long affordable = ceiling.subtract(first).divide(increment, 0, RoundingMode.FLOOR).longValueExact() + 1;
        long last = (affordable % 2 == 1) == oddSteps ? affordable : affordable - 1;

        return !oddSteps && last < 2 ? 0 : last;
    }

    static BigDecimal amountOf(long step, BigDecimal first, BigDecimal increment) {
        return first.add(increment.multiply(BigDecimal.valueOf(step - 1)));
    }
}
