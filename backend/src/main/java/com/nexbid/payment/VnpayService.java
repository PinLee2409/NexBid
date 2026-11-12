package com.nexbid.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.payment.entity.Payment;
import com.nexbid.payment.entity.PaymentTransaction;
import com.nexbid.payment.repository.PaymentTransactionRepository;

/**
 * EN: Paying through VNPay. Checkout records an attempt and hands the buyer a signed link to VNPay's page; VNPay then
 *     reports the result twice — server to server (IPN) and through the buyer's browser on the return URL. Both land
 *     in {@link #confirm}, which checks the signature and amount and settles the attempt exactly once.
 * VI: Thanh toán qua VNPay. Checkout ghi lại một lần thử và đưa người mua link có chữ ký sang trang VNPay; sau đó VNPay
 *     báo kết quả hai lần — server tới server (IPN) và qua trình duyệt người mua ở return URL. Cả hai đều vào
 *     {@link #confirm}, nơi kiểm chữ ký và số tiền rồi chốt lần thử đúng một lần.
 */
@Service
public class VnpayService {

    private static final Logger log = LoggerFactory.getLogger(VnpayService.class);

    /** EN: VNPay reads and writes its timestamps in Vietnam time. / VI: VNPay đọc và ghi thời gian theo giờ Việt Nam. */
    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(VIETNAM);

    /** EN: vnp_ResponseCode for a payment that went through. / VI: vnp_ResponseCode cho giao dịch thành công. */
    static final String APPROVED = "00";

    private final PaymentService payments;
    private final PaymentTransactionRepository transactions;
    private final VnpayProperties properties;
    private final VnpaySigner signer;

    VnpayService(PaymentService payments, PaymentTransactionRepository transactions, VnpayProperties properties) {
        this.payments = payments;
        this.transactions = transactions;
        this.properties = properties;
        this.signer = new VnpaySigner(properties.hashSecret());
    }

    /** EN: Where to send the buyer. / VI: Nơi đưa người mua tới. */
    public record Checkout(String paymentUrl, String txnRef) {
    }

    /**
     * EN: Starts an attempt for the winner's payment. The link expires with the configured timeout, and never after
     *     the payment's own deadline.
     * VI: Bắt đầu một lần thử cho khoản của người thắng. Link hết hạn theo timeout đã cấu hình, và không bao giờ sau
     *     hạn chót của chính khoản thanh toán.
     */
    @Transactional
    public Checkout startCheckout(UUID userId, UUID paymentId, String clientIp, String locale) {
        Instant now = Instant.now();
        Payment payment = payments.payableBy(userId, paymentId, now);

        String txnRef = newTxnRef(now);
        transactions.save(new PaymentTransaction(payment.getId(), txnRef, payment.getAmount()));

        Instant expires = now.plus(properties.timeout());
        if (payment.getExpiredAt().isBefore(expires)) {
            expires = payment.getExpiredAt();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("vnp_Version", "2.1.0");
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", properties.tmnCode());
        fields.put("vnp_Amount", minorUnits(payment.getAmount()));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        // EN: VNPay wants plain ASCII here. / VI: VNPay yêu cầu ASCII không dấu ở trường này.
        fields.put("vnp_OrderInfo", "NexBid payment " + payment.getId());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vi".equals(locale) ? "vn" : "en");
        fields.put("vnp_ReturnUrl", properties.returnUrl());
        fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
        fields.put("vnp_CreateDate", STAMP.format(now));
        fields.put("vnp_ExpireDate", STAMP.format(expires));

        return new Checkout(properties.payUrl() + "?" + signer.signedQuery(fields), txnRef);
    }

    /** EN: How a report from VNPay was handled. / VI: Lời báo từ VNPay đã được xử lý ra sao. */
    public enum Outcome {
        /** EN: Settled now, paid or failed. / VI: Vừa được chốt, đã trả hoặc thất bại. */
        CONFIRMED,
        /** EN: This attempt had been settled before. / VI: Lần thử này đã được chốt từ trước. */
        ALREADY_CONFIRMED,
        NOT_FOUND,
        INVALID_AMOUNT,
        INVALID_SIGNATURE
    }

    /**
     * EN: The result of a report. {@code paid} says whether the payment is paid now; {@code responseCode} is VNPay's
     *     own code, e.g. 24 when the buyer cancelled on VNPay's page.
     * VI: Kết quả của một lời báo. {@code paid} cho biết khoản đã được trả hay chưa; {@code responseCode} là mã của
     *     VNPay, ví dụ 24 khi người mua huỷ trên trang VNPay.
     */
    public record Confirmation(Outcome outcome, UUID paymentId, boolean paid, String responseCode) {
    }

    /**
     * EN: Applies a report from VNPay (IPN or return URL). Idempotent: the attempt row is locked and settled once, so
     *     the IPN and the buyer's return, or VNPay retrying its IPN, never charge twice.
     * VI: Áp một lời báo từ VNPay (IPN hoặc return URL). Idempotent: dòng lần thử được khoá và chỉ chốt một lần, nên IPN
     *     và lúc người mua quay về, hay VNPay gửi lại IPN, không bao giờ trừ tiền hai lần.
     */
    @Transactional
    public Confirmation confirm(Map<String, String> fields) {
        if (!signer.verify(fields)) {
            return new Confirmation(Outcome.INVALID_SIGNATURE, null, false, null);
        }

        String txnRef = fields.get("vnp_TxnRef");
        PaymentTransaction attempt = txnRef == null ? null : transactions.findByTxnRefForUpdate(txnRef).orElse(null);
        if (attempt == null) {
            return new Confirmation(Outcome.NOT_FOUND, null, false, null);
        }
        if (!minorUnits(attempt.getAmount()).equals(fields.get("vnp_Amount"))) {
            return new Confirmation(Outcome.INVALID_AMOUNT, attempt.getPaymentId(), false, null);
        }
        if (!attempt.isPending()) {
            return new Confirmation(Outcome.ALREADY_CONFIRMED, attempt.getPaymentId(),
                    PaymentTransaction.SUCCESS.equals(attempt.getStatus()), attempt.getResponseCode());
        }

        String responseCode = fields.get("vnp_ResponseCode");
        // EN: The return URL may lack vnp_TransactionStatus on older merchant setups; the response code still decides.
        // VI: Return URL có thể thiếu vnp_TransactionStatus ở cấu hình merchant cũ; mã phản hồi vẫn là thứ quyết định.
        String transactionStatus = fields.getOrDefault("vnp_TransactionStatus", responseCode);
        boolean success = APPROVED.equals(responseCode) && APPROVED.equals(transactionStatus);

        attempt.settle(success, responseCode, fields.get("vnp_TransactionNo"), fields.get("vnp_BankCode"));
        transactions.save(attempt);

        PaymentService.Settled settled = payments.settleAttempt(attempt.getPaymentId(), success, Instant.now());
        if (success && settled == PaymentService.Settled.TOO_LATE) {
            log.warn("VNPay charged attempt {} for payment {} after its window closed; refund it from the VNPay portal",
                    txnRef, attempt.getPaymentId());
        }
        if (success && settled == PaymentService.Settled.ALREADY_PAID) {
            log.warn("VNPay charged attempt {} for payment {} that an earlier attempt had already paid; refund it",
                    txnRef, attempt.getPaymentId());
        }
        boolean paid = settled == PaymentService.Settled.PAID || settled == PaymentService.Settled.ALREADY_PAID;
        return new Confirmation(Outcome.CONFIRMED, attempt.getPaymentId(), paid, responseCode);
    }

    /** EN: VND has no minor unit, but VNPay still wants the amount times 100. / VI: VND không có đơn vị lẻ, nhưng VNPay vẫn cần số tiền nhân 100. */
    static String minorUnits(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).toPlainString();
    }

    /** EN: Unique, short, ASCII: time plus randomness. / VI: Duy nhất, ngắn, ASCII: thời gian kèm phần ngẫu nhiên. */
    private static String newTxnRef(Instant now) {
        byte[] random = new byte[5];
        ThreadLocalRandom.current().nextBytes(random);
        return STAMP.format(now) + HexFormat.of().formatHex(random).toUpperCase();
    }
}
