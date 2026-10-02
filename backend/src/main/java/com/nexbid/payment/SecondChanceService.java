package com.nexbid.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionService.UnpaidLot;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.bid.BidService;
import com.nexbid.bid.BidService.RunnerUp;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.payment.entity.SecondChanceOffer;
import com.nexbid.payment.repository.SecondChanceOfferRepository;

/**
 * EN: Second-chance offers (spec §17). When a winner lets the payment lapse, the seller may offer the lot once to
 *     the runner-up, at the runner-up's own highest bid. They have 24 hours; accepting opens a payment with the
 *     usual 48-hour window, and declining or letting it lapse leaves the lot cancelled.
 * VI: Đề nghị cơ hội thứ hai (spec §17). Khi người thắng để quá hạn thanh toán, người bán được đề nghị lô một lần
 *     cho người thứ hai, với giá cao nhất của chính người đó. Họ có 24 giờ; nhận thì mở khoản thanh toán với hạn 48
 *     giờ như thường lệ, còn từ chối hay để quá hạn thì lô vẫn bị huỷ.
 */
@Service
public class SecondChanceService {

    private final SecondChanceOfferRepository offers;
    private final PaymentService payments;
    private final AuctionService auctions;
    private final BidService bids;
    private final ApplicationEventPublisher events;
    private final Duration window;

    SecondChanceService(
            SecondChanceOfferRepository offers,
            PaymentService payments,
            AuctionService auctions,
            BidService bids,
            ApplicationEventPublisher events,
            @Value("${nexbid.payment.offer-window}") Duration window) {

        this.offers = offers;
        this.payments = payments;
        this.auctions = auctions;
        this.bids = bids;
        this.events = events;
        this.window = window;
    }

    /**
     * EN: Every unpaid lot of this seller and every offer they made — an accepted one stays listed after its lot
     *     is sold again.
     * VI: Mọi lô bị bỏ không trả của người bán này và mọi đề nghị họ đã gửi — đề nghị đã được nhận vẫn còn trong
     *     danh sách sau khi lô được bán lại.
     */
    @Transactional(readOnly = true)
    public List<SecondChanceView> forSeller(UUID sellerId) {
        Instant now = Instant.now();
        Map<UUID, SecondChanceOffer> made = offers.findBySellerId(sellerId).stream()
                .collect(Collectors.toMap(SecondChanceOffer::getAuctionId, Function.identity()));

        List<SecondChanceView> views = new ArrayList<>();
        for (UnpaidLot lot : auctions.unpaidLotsOf(sellerId)) {
            SecondChanceOffer offer = made.remove(lot.auctionId());
            if (offer != null) {
                views.add(new SecondChanceView(lot.auctionId(), offer.getAmount(), false, details(offer, now)));
                continue;
            }
            Optional<RunnerUp> runnerUp = bids.runnerUpOf(lot.auctionId(), lot.winnerId());
            views.add(new SecondChanceView(lot.auctionId(), runnerUp.map(RunnerUp::amount).orElse(null),
                    runnerUp.isPresent() && auctions.productFree(lot), null));
        }
        made.values().forEach(offer ->
                views.add(new SecondChanceView(offer.getAuctionId(), offer.getAmount(), false, details(offer, now))));
        return views;
    }

    /**
     * EN: The seller offers an unpaid lot to its runner-up. The product is reserved in the same step, so it cannot
     *     be listed again while the offer is open.
     * VI: Người bán đề nghị một lô bị bỏ không trả cho người thứ hai. Sản phẩm được giữ chỗ cùng bước đó, để không bị
     *     đăng lại khi đề nghị còn mở.
     */
    @Transactional
    public OfferView.Details offer(UUID sellerId, UUID auctionId) {
        if (offers.existsByAuctionId(auctionId)) {
            throw new BusinessException(ErrorCode.OFFER_ALREADY_MADE, "This lot has already been offered once");
        }
        UnpaidLot lot = auctions.reserveForSecondChance(sellerId, auctionId);
        RunnerUp runnerUp = bids.runnerUpOf(auctionId, lot.winnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_RUNNER_UP, "Nobody else bid on this lot"));

        Instant now = Instant.now();
        SecondChanceOffer offer = offers.saveAndFlush(new SecondChanceOffer(
                auctionId, sellerId, runnerUp.bidderId(), runnerUp.amount(), now, now.plus(window)));
        events.publishEvent(new SecondChanceEvents.Offered(offer.getId(), auctionId, sellerId, runnerUp.bidderId(),
                runnerUp.amount(), offer.getExpiresAt(), now));
        return details(offer, now);
    }

    /** EN: Offers made to the caller, newest first. / VI: Các đề nghị gửi tới người gọi, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<OfferView> mine(UUID buyerId) {
        List<SecondChanceOffer> mine = offers.findByBuyerIdOrderByCreatedAtDesc(buyerId);
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                mine.stream().map(SecondChanceOffer::getAuctionId).toList());
        Instant now = Instant.now();
        return mine.stream().map(offer -> new OfferView(details(offer, now), lots.get(offer.getAuctionId()))).toList();
    }

    /**
     * EN: The runner-up takes the lot: it is closed again in their name and their payment opens, with the order
     *     that goes with it.
     * VI: Người thứ hai nhận lô: lô đóng lại đứng tên họ và khoản thanh toán của họ được mở, kèm đơn hàng đi cùng.
     */
    @Transactional
    public OfferView.Details accept(UUID buyerId, UUID offerId) {
        SecondChanceOffer offer = open(buyerId, offerId);
        Instant now = Instant.now();

        auctions.awardToRunnerUp(offer.getAuctionId(), buyerId);
        payments.openFor(offer.getAuctionId(), buyerId, offer.getAmount(), now);
        offer.settle(OfferStatus.ACCEPTED, now);
        offers.save(offer);
        events.publishEvent(new SecondChanceEvents.Accepted(
                offer.getId(), offer.getAuctionId(), offer.getSellerId(), buyerId, offer.getAmount(), now));
        return details(offer, now);
    }

    @Transactional
    public OfferView.Details decline(UUID buyerId, UUID offerId) {
        SecondChanceOffer offer = open(buyerId, offerId);
        Instant now = Instant.now();
        close(offer, OfferStatus.DECLINED, now);
        return details(offer, now);
    }

    /**
     * EN: Closes offers nobody answered in time; each gives its product back to the seller.
     * VI: Đóng các đề nghị không ai trả lời kịp; mỗi đề nghị trả sản phẩm về cho người bán.
     */
    @Transactional
    public int expireOverdue(Instant now, int batchSize) {
        List<SecondChanceOffer> overdue = offers.findOverdue(now, PageRequest.of(0, batchSize));
        overdue.forEach(offer -> close(offer, OfferStatus.EXPIRED, now));
        return overdue.size();
    }

    private void close(SecondChanceOffer offer, OfferStatus outcome, Instant now) {
        offer.settle(outcome, now);
        offers.save(offer);
        auctions.releaseSecondChance(offer.getAuctionId());
        events.publishEvent(new SecondChanceEvents.Declined(offer.getId(), offer.getAuctionId(), offer.getSellerId(),
                offer.getBuyerId(), outcome == OfferStatus.EXPIRED, now));
    }

    /**
     * EN: Locked and still open. Someone else's offer answers 404, like one that does not exist.
     * VI: Có khoá và còn mở. Đề nghị của người khác trả 404, như đề nghị không tồn tại.
     */
    private SecondChanceOffer open(UUID buyerId, UUID offerId) {
        SecondChanceOffer offer = offers.findByIdForUpdate(offerId)
                .filter(found -> found.getBuyerId().equals(buyerId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.OFFER_NOT_FOUND, "No offer with id " + offerId));
        // EN: The clock, not the status: the expiry job may not have run yet. / VI: Đồng hồ quyết định, không phải trạng thái: job hết hạn có thể chưa chạy.
        if (offer.getStatus() != OfferStatus.PENDING || offer.isOverdue(Instant.now())) {
            throw new BusinessException(ErrorCode.OFFER_NOT_PENDING, "This offer is no longer open");
        }
        return offer;
    }

    /** EN: An open offer past its deadline reads as EXPIRED at once. / VI: Đề nghị còn mở mà quá hạn thì đọc ra EXPIRED ngay. */
    private static OfferView.Details details(SecondChanceOffer offer, Instant now) {
        OfferStatus status = offer.getStatus() == OfferStatus.PENDING && offer.isOverdue(now)
                ? OfferStatus.EXPIRED
                : offer.getStatus();
        return new OfferView.Details(offer.getId(), offer.getAuctionId(), offer.getAmount(), status,
                offer.getExpiresAt(), offer.getCreatedAt(), offer.getDecidedAt());
    }
}
