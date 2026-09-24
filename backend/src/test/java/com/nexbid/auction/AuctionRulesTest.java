package com.nexbid.auction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.nexbid.auction.entity.Auction;

/**
 * EN: The bidding arithmetic (spec §8), tested without a database. Function 20 will lean on exactly this,
 *     so it is worth pinning down on its own.
 * VI: Phần tính toán khi trả giá (spec §8), kiểm chứng không cần database. Chức năng 20 sẽ dựa hẳn vào đây,
 *     nên đáng để chốt riêng.
 */
class AuctionRulesTest {

    private static Auction lot(String startingPrice, String currentPrice, String increment, int bidCount) {
        Auction auction = new Auction(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal(startingPrice),
                new BigDecimal(increment),
                Instant.now().minus(Duration.ofMinutes(10)),
                Instant.now().plus(Duration.ofHours(1)),
                false, 30, 120);

        // EN: Current price and bid count only ever move through bidding, so tests set them directly.
        // VI: Giá hiện tại và số lượt chỉ thay đổi qua việc trả giá, nên test đặt thẳng vào.
        ReflectionTestUtils.setField(auction, "currentPrice", new BigDecimal(currentPrice));
        ReflectionTestUtils.setField(auction, "bidCount", bidCount);

        return auction;
    }

    @Test
    void theFirstBidOnlyHasToMeetTheOpeningPrice() {
        Auction auction = lot("1000", "1000", "50", 0);

        // EN: Not 1050 — nobody should have to beat a price no one has offered.
        // VI: Không phải 1050 — không ai phải vượt qua một mức giá chưa ai đưa ra.
        assertThat(AuctionRules.minimumNextBid(auction)).isEqualByComparingTo("1000");
    }

    @Test
    void laterBidsMustClearTheCurrentPriceByAFullIncrement() {
        Auction auction = lot("1000", "1200", "50", 3);

        assertThat(AuctionRules.minimumNextBid(auction)).isEqualByComparingTo("1250");
    }

    @Test
    void decimalsAreExactNotApproximate() {
        Auction auction = lot("0.10", "0.30", "0.10", 2);

        // EN: 0.30 + 0.10 in binary floating point is 0.4000000000000001. In money it is 0.40.
        // VI: 0.30 + 0.10 kiểu số thực nhị phân ra 0.4000000000000001. Với tiền thì phải là 0.40.
        assertThat(AuctionRules.minimumNextBid(auction)).isEqualByComparingTo("0.40");
    }

    @Test
    void anActiveLotInsideItsWindowIsOpen() {
        Auction auction = lot("1000", "1000", "50", 0);
        auction.setStatus(AuctionStatus.ACTIVE);

        assertThat(AuctionRules.isOpenForBidding(auction, Instant.now())).isTrue();
    }

    @Test
    void aLotMarkedActiveWhoseClockRanOutIsNotOpen() {
        Auction auction = lot("1000", "1000", "50", 0);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setEndTime(Instant.now().minus(Duration.ofSeconds(1)));

        // EN: The moment between the clock running out and the scheduler noticing. Status alone would
        //     have let a bid through.
        // VI: Khoảnh khắc giữa lúc hết giờ và lúc scheduler nhận ra. Chỉ nhìn trạng thái thì một lượt
        //     trả giá đã lọt qua.
        assertThat(AuctionRules.isOpenForBidding(auction, Instant.now())).isFalse();
    }

    @Test
    void aScheduledLotIsNotOpenYet() {
        Auction auction = lot("1000", "1000", "50", 0);
        auction.setStatus(AuctionStatus.SCHEDULED);

        assertThat(AuctionRules.isOpenForBidding(auction, Instant.now())).isFalse();
    }

    @Test
    void anActiveLotBeforeItsStartTimeIsNotOpen() {
        Auction auction = lot("1000", "1000", "50", 0);
        auction.setStatus(AuctionStatus.ACTIVE);
        auction.setStartTime(Instant.now().plus(Duration.ofMinutes(5)));

        assertThat(AuctionRules.isOpenForBidding(auction, Instant.now())).isFalse();
    }

    @Test
    void anEndedLotIsNotOpen() {
        Auction auction = lot("1000", "1000", "50", 0);
        auction.setStatus(AuctionStatus.ENDED);

        assertThat(AuctionRules.isOpenForBidding(auction, Instant.now())).isFalse();
    }
}
