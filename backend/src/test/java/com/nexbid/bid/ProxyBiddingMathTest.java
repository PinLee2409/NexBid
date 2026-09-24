package com.nexbid.bid;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * EN: The shortcut in ProxyBidding must end exactly where playing the exchange out one step at a time
 *     would. Checked against a brute-force replay on thousands of random cases.
 * VI: Lối tắt trong ProxyBidding phải dừng đúng chỗ mà việc chạy từng bước một sẽ dừng. Được so với một bản
 *     chạy vét cạn trên hàng nghìn trường hợp ngẫu nhiên.
 */
class ProxyBiddingMathTest {

    /** EN: Result of an exchange: how many steps were taken. / VI: Kết quả một cuộc giằng co: đi được bao nhiêu bước. */
    private static long shortcut(BigDecimal first, BigDecimal increment, BigDecimal challengerMax, BigDecimal defenderMax) {
        long challengerLast = ProxyBidding.lastAffordable(challengerMax, first, increment, true);
        long defenderLast = defenderMax == null ? 0 : ProxyBidding.lastAffordable(defenderMax, first, increment, false);
        return Math.min(challengerLast, defenderLast) + 1;
    }

    /** EN: The slow, obviously-correct way: take turns until someone cannot pay. / VI: Cách chậm nhưng hiển nhiên đúng: thay phiên tới khi có người không trả nổi. */
    private static long replay(BigDecimal first, BigDecimal increment, BigDecimal challengerMax, BigDecimal defenderMax) {
        long step = 1;
        while (true) {
            BigDecimal price = ProxyBidding.amountOf(step, first, increment);
            BigDecimal ceiling = step % 2 == 1 ? challengerMax : defenderMax;
            if (ceiling == null || price.compareTo(ceiling) > 0) {
                return step - 1;
            }
            step++;
        }
    }

    @Test
    void theShortcutMatchesPlayingItOutOnTenThousandRandomExchanges() {
        Random random = new Random(20261001);

        for (int i = 0; i < 10_000; i++) {
            BigDecimal increment = BigDecimal.valueOf(1 + random.nextInt(50) * 100L);
            BigDecimal first = BigDecimal.valueOf(1_000 + random.nextInt(100_000));
            // EN: The challenger can always afford step one, or it would not be a challenger.
            // VI: Bên thách đấu luôn trả nổi bước một, nếu không đã không phải bên thách đấu.
            BigDecimal challengerMax = first.add(BigDecimal.valueOf(random.nextInt(200_000)));
            BigDecimal defenderMax = random.nextInt(4) == 0
                    ? null
                    : first.subtract(BigDecimal.valueOf(5_000)).add(BigDecimal.valueOf(random.nextInt(205_000)));

            assertThat(shortcut(first, increment, challengerMax, defenderMax))
                    .as("first=%s increment=%s challenger=%s defender=%s", first, increment, challengerMax, defenderMax)
                    .isEqualTo(replay(first, increment, challengerMax, defenderMax));
        }
    }

    @Test
    void theSpecExampleIsOneStep() {
        // EN: Spec §14: B bids 11m, A (max 20m, increment 0.5m) answers once at 11.5m.
        // VI: Spec §14: B trả 11 triệu, A (tối đa 20 triệu, bước 0,5 triệu) đáp trả một lần ở 11,5 triệu.
        BigDecimal first = new BigDecimal("11500000");
        assertThat(shortcut(first, new BigDecimal("500000"), new BigDecimal("20000000"), null)).isEqualTo(1);
        assertThat(ProxyBidding.amountOf(1, first, new BigDecimal("500000"))).isEqualByComparingTo("11500000");
    }

    @Test
    void aTinyIncrementDoesNotMeanATinyAmountOfWork() {
        // EN: 1 VND steps between 10m and 20m would be ten million bids played out; worked out it is instant.
        // VI: Bước 1 đồng từ 10 triệu tới 20 triệu mà chạy từng bước là mười triệu lượt; tính ra thì tức thì.
        long steps = shortcut(new BigDecimal("10000001"), BigDecimal.ONE,
                new BigDecimal("15000000"), new BigDecimal("20000000"));

        assertThat(steps % 2).as("the defender, with the higher ceiling, wins").isZero();
        // EN: The challenger takes odd steps and tops out at 14,999,999, so the defender answers at 15,000,000.
        // VI: Bên thách đấu đi bước lẻ và dừng ở 14.999.999, nên bên giữ vị trí đáp trả ở 15.000.000.
        assertThat(ProxyBidding.amountOf(steps, new BigDecimal("10000001"), BigDecimal.ONE))
                .isEqualByComparingTo("15000000");
    }
}
