package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auction.AuctionService;
import com.nexbid.auction.AuctionSummaryView;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.payment.entity.Payment;
import com.nexbid.payment.repository.PaymentRepository;

/**
 * EN: The winner's payment (guide §32, spec §17): created at the close, paid by the winner alone, and
 *     expired when its window runs out.
 * VI: Khoản thanh toán của người thắng (guide §32, spec §17): tạo lúc đóng phiên, chỉ người thắng được trả,
 *     và hết hạn khi quá thời gian cho phép.
 */
@Service
public class PaymentService {

    private final PaymentRepository payments;
    private final AuctionService auctions;
    private final ApplicationEventPublisher events;
    private final Duration window;

    PaymentService(
            PaymentRepository payments,
            AuctionService auctions,
            ApplicationEventPublisher events,
            @Value("${nexbid.payment.window}") Duration window) {

        this.payments = payments;
        this.auctions = auctions;
        this.events = events;
        this.window = window;
    }

    /**
     * EN: Opens the winner's payment. Runs inside the transaction that closed the lot, so a winner never
     *     exists without a payment; the window counts from the close, not from whenever this ran.
     * VI: Mở khoản thanh toán cho người thắng. Chạy trong transaction đã đóng phiên, nên không bao giờ có người
     *     thắng mà thiếu khoản thanh toán; hạn tính từ giờ đóng, không từ lúc đoạn này tình cờ chạy.
     */
    @Transactional
    void openFor(UUID auctionId, UUID winnerId, BigDecimal amount, Instant closedAt) {
        payments.save(new Payment(auctionId, winnerId, amount, closedAt.plus(window)));
    }

    public List<PaymentView> listFor(UUID userId) {
        List<Payment> mine = payments.findByUserIdOrderByCreatedAtDesc(userId);
        Map<UUID, AuctionSummaryView> lots = auctions.participantSummariesOf(
                mine.stream().map(Payment::getAuctionId).toList());

        Instant now = Instant.now();
        return mine.stream().map(payment -> view(payment, lots.get(payment.getAuctionId()), now)).toList();
    }

    public PaymentView get(UUID userId, UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> notFound(paymentId));

        return view(payment, Instant.now());
    }

    /**
     * EN: The demo switch (spec §17). Only the winner can pay; anyone else hears "not found", as for an id
     *     that does not exist. A failed attempt may be retried until the deadline.
     * VI: Công tắc demo (spec §17). Chỉ người thắng được trả; ai khác nhận "không tìm thấy", như với id không
     *     tồn tại. Lần thử hỏng được thử lại tới hạn chót.
     */
    @Transactional
    public PaymentView pay(UUID userId, UUID paymentId, PaymentOutcome outcome) {
        Payment payment = payments.findByIdForUpdate(paymentId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(() -> notFound(paymentId));

        Instant now = Instant.now();

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_PAID, "This payment has already been made");
        }
        // EN: The clock, not the status: the expiry job may not have run yet. / VI: Đồng hồ quyết định, không phải trạng thái: job hết hạn có thể chưa chạy.
        if (payment.getStatus() == PaymentStatus.EXPIRED || payment.isOverdue(now)) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED, "The payment window for this lot has closed");
        }

        payment.settle(outcome == PaymentOutcome.SUCCESS ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payments.saveAndFlush(payment);

        if (outcome == PaymentOutcome.SUCCESS) {
            events.publishEvent(new PaymentEvents.Succeeded(
                    payment.getId(), payment.getAuctionId(), userId, payment.getAmount(), now));
        }

        return view(payment, now);
    }

    /**
     * EN: Closes payments whose window ran out, in batches. Each expiry also cancels its lot so the product
     *     can be listed again.
     * VI: Đóng các khoản đã quá hạn, theo từng đợt. Mỗi lần hết hạn cũng huỷ lô tương ứng để sản phẩm được
     *     đăng lại.
     */
    @Transactional
    public int expireOverdue(Instant now, int batchSize) {
        List<Payment> overdue = payments.findOverdue(now, PageRequest.of(0, batchSize));

        for (Payment payment : overdue) {
            payment.settle(PaymentStatus.EXPIRED);
            auctions.cancelUnpaid(payment.getAuctionId());
            events.publishEvent(new PaymentEvents.Expired(
                    payment.getId(), payment.getAuctionId(), payment.getUserId(), payment.getAmount(), now));
        }

        payments.saveAll(overdue);
        return overdue.size();
    }

    private PaymentView view(Payment payment, Instant now) {
        AuctionSummaryView lot = auctions.participantSummariesOf(List.of(payment.getAuctionId()))
                .get(payment.getAuctionId());
        return view(payment, lot, now);
    }

    /**
     * EN: An open payment past its deadline reads as EXPIRED straight away, even before the job has run.
     * VI: Khoản còn mở mà đã quá hạn thì đọc ra EXPIRED ngay, kể cả khi job chưa kịp chạy.
     */
    private static PaymentView view(Payment payment, AuctionSummaryView lot, Instant now) {
        PaymentStatus status = payment.getStatus().isOpen() && payment.isOverdue(now)
                ? PaymentStatus.EXPIRED
                : payment.getStatus();

        return new PaymentView(
                new PaymentView.Details(
                        payment.getId(),
                        payment.getAuctionId(),
                        payment.getUserId(),
                        payment.getAmount(),
                        status,
                        payment.getExpiredAt(),
                        payment.getCreatedAt(),
                        payment.getUpdatedAt()),
                lot);
    }

    private static ResourceNotFoundException notFound(UUID paymentId) {
        return new ResourceNotFoundException(ErrorCode.PAYMENT_NOT_FOUND, "No payment with id " + paymentId);
    }
}
