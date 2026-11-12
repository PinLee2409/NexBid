package com.nexbid.mail;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * EN: What an email is about. Every field is optional; each template uses the ones it needs.
 * VI: Nội dung mà một email nói tới. Mọi trường đều tuỳ chọn; mỗi mẫu dùng những trường nó cần.
 *
 * @param auctionId EN: the lot, for links and the outbid throttle / VI: lô, dùng cho link và giới hạn email bị vượt giá
 * @param lotTitle  EN: the product's name / VI: tên sản phẩm
 * @param amount    EN: a price in VND / VI: một mức giá bằng VND
 * @param until     EN: a deadline / VI: một hạn chót
 * @param reason    EN: free text, e.g. why a request was rejected / VI: văn bản tự do, ví dụ lý do yêu cầu bị từ chối
 * @param tracking  EN: carrier and tracking number of a parcel / VI: đơn vị vận chuyển và mã vận đơn của kiện hàng
 */
public record MailFacts(UUID auctionId, String lotTitle, BigDecimal amount, Instant until, String reason, String tracking) {

    public static MailFacts none() {
        return new MailFacts(null, null, null, null, null, null);
    }

    public static MailFacts lot(UUID auctionId, String lotTitle, BigDecimal amount) {
        return new MailFacts(auctionId, lotTitle, amount, null, null, null);
    }

    public MailFacts withUntil(Instant deadline) {
        return new MailFacts(auctionId, lotTitle, amount, deadline, reason, tracking);
    }

    public MailFacts withReason(String text) {
        return new MailFacts(auctionId, lotTitle, amount, until, text, tracking);
    }

    public MailFacts withTracking(String carrier, String trackingNumber) {
        String label = java.util.stream.Stream.of(carrier, trackingNumber)
                .filter(part -> part != null && !part.isBlank())
                .collect(java.util.stream.Collectors.joining(" · "));
        return new MailFacts(auctionId, lotTitle, amount, until, reason, label.isEmpty() ? null : label);
    }
}
