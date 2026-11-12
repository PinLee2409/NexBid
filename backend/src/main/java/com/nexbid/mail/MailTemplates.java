package com.nexbid.mail;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

import org.springframework.web.util.HtmlUtils;

/**
 * EN: Bilingual emails: Vietnamese first, then English, in one message, with the same button for both. Amounts are VND
 *     written each language's way; times are Vietnam time. Everything a user typed (names, lot titles, reasons) is
 *     HTML-escaped.
 * VI: Email song ngữ: tiếng Việt trước, tiếng Anh sau, trong cùng một thư, dùng chung một nút. Số tiền là VND viết theo
 *     cách của từng ngôn ngữ; thời gian theo giờ Việt Nam. Mọi thứ người dùng nhập (tên, tên lô, lý do) đều được
 *     escape HTML.
 */
final class MailTemplates {

    /** EN: A ready email. / VI: Một email đã soạn xong. */
    record Rendered(String subject, String html, String text) {
    }

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(VIETNAM);

    private final String siteUrl;

    MailTemplates(String siteUrl) {
        this.siteUrl = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
    }

    /** EN: One language's half of an email. / VI: Một nửa email theo một ngôn ngữ. */
    private record Half(String heading, String body, String button) {
    }

    Rendered render(MailKind kind, String name, MailFacts facts) {
        return render(kind, name, facts, null);
    }

    /**
     * EN: {@code unsubscribeUrl} is set only for optional emails; it adds a one-click link to turn them off.
     * VI: {@code unsubscribeUrl} chỉ có ở email có thể tắt; nó thêm link một chạm để tắt chúng.
     */
    Rendered render(MailKind kind, String name, MailFacts facts, String unsubscribeUrl) {
        Rendered body = body(kind, name, facts);
        String settings = siteUrl + "/profile#email";
        String footer = "<a href=\"" + HtmlUtils.htmlEscape(settings) + "\" style=\"color:#777\">Cài đặt email · Email settings</a>"
                + (unsubscribeUrl == null ? "" : " &nbsp;|&nbsp; <a href=\"" + HtmlUtils.htmlEscape(unsubscribeUrl)
                        + "\" style=\"color:#777\">Tắt email bị vượt giá · Turn off outbid emails</a>");
        String text = body.text() + "\nCài đặt email · Email settings: " + settings + "\n"
                + (unsubscribeUrl == null ? "" : "Tắt email bị vượt giá · Turn off outbid emails: " + unsubscribeUrl + "\n");
        return new Rendered(body.subject(), body.html().replace(FOOTER_SLOT, footer), text);
    }

    private static final String FOOTER_SLOT = "<!--footer-links-->";

    private Rendered body(MailKind kind, String name, MailFacts facts) {
        String lot = facts.lotTitle() == null ? "" : facts.lotTitle();
        String vnd = facts.amount() == null ? "" : money(facts.amount(), Locale.forLanguageTag("vi-VN"));
        String vndEn = facts.amount() == null ? "" : money(facts.amount(), Locale.US);
        String until = facts.until() == null ? "" : WHEN.format(facts.until()) + " (GMT+7)";
        String reason = facts.reason() == null ? "" : facts.reason();
        String trackingVi = facts.tracking() == null ? "" : "<br>Vận chuyển: <b>" + facts.tracking() + "</b>";
        String trackingEn = facts.tracking() == null ? "" : "<br>Shipping: <b>" + facts.tracking() + "</b>";
        String lotLink = facts.auctionId() == null ? siteUrl : siteUrl + "/auctions/" + facts.auctionId();

        return switch (kind) {
            case WELCOME -> build("Chào mừng bạn đến với NexBid", "Welcome to NexBid", siteUrl + "/auctions",
                    new Half("Chào mừng, " + name + "!",
                            "Tài khoản của bạn đã sẵn sàng. Hãy xem các phiên đang diễn ra và đặt lượt trả giá đầu tiên.",
                            "Xem các phiên"),
                    new Half("Welcome, " + name + "!",
                            "Your account is ready. Browse the live lots and place your first bid.",
                            "Browse lots"));
            case OUTBID -> build("Bạn đã bị trả giá cao hơn: " + lot, "You've been outbid: " + lot, lotLink,
                    new Half("Có người trả giá cao hơn bạn",
                            "Giá hiện tại của <b>" + lot + "</b> là <b>" + vnd + "</b>. Trả giá lại để giữ vị trí dẫn đầu.",
                            "Trả giá lại"),
                    new Half("Someone bid higher",
                            "<b>" + lot + "</b> is now at <b>" + vndEn + "</b>. Bid again to take back the lead.",
                            "Bid again"));
            case AUCTION_WON -> build("Bạn đã thắng: " + lot, "You won: " + lot, siteUrl + "/payments",
                    new Half("Chúc mừng, bạn đã thắng!",
                            "Bạn thắng <b>" + lot + "</b> với giá <b>" + vnd + "</b>. Vui lòng thanh toán trước <b>"
                                    + until + "</b>, nếu không giao dịch sẽ bị huỷ.",
                            "Thanh toán ngay"),
                    new Half("Congratulations, you won!",
                            "You won <b>" + lot + "</b> for <b>" + vndEn + "</b>. Please pay before <b>" + until
                                    + "</b>, or the sale will be cancelled.",
                            "Pay now"));
            case PAYMENT_REMINDER -> build("Sắp hết hạn thanh toán: " + lot, "Payment due soon: " + lot,
                    siteUrl + "/payments",
                    new Half("Nhắc thanh toán",
                            "Khoản <b>" + vnd + "</b> cho <b>" + lot + "</b> cần được thanh toán trước <b>" + until + "</b>.",
                            "Thanh toán ngay"),
                    new Half("Payment reminder",
                            "Your payment of <b>" + vndEn + "</b> for <b>" + lot + "</b> is due before <b>" + until + "</b>.",
                            "Pay now"));
            case PAYMENT_RECEIPT -> build("Đã nhận thanh toán: " + lot, "Payment received: " + lot, siteUrl + "/orders",
                    new Half("Thanh toán thành công",
                            "Chúng tôi đã nhận <b>" + vnd + "</b> cho <b>" + lot
                                    + "</b>. Người bán sẽ gửi hàng sớm; bạn có thể theo dõi đơn hàng bất cứ lúc nào.",
                            "Xem đơn hàng"),
                    new Half("Payment successful",
                            "We received <b>" + vndEn + "</b> for <b>" + lot
                                    + "</b>. The seller will ship soon; you can follow your order at any time.",
                            "View order"));
            case PAYMENT_EXPIRED -> build("Đã quá hạn thanh toán: " + lot, "Payment window closed: " + lot, lotLink,
                    new Half("Giao dịch đã bị huỷ",
                            "<b>" + lot + "</b> không được thanh toán đúng hạn nên giao dịch đã bị huỷ.",
                            "Xem lô"),
                    new Half("The sale was cancelled",
                            "<b>" + lot + "</b> was not paid in time, so the sale was cancelled.",
                            "View lot"));
            case SALE_CANCELLED -> build("Giao dịch bị huỷ: " + lot, "Sale cancelled: " + lot,
                    siteUrl + "/seller/auctions",
                    new Half("Người thắng không thanh toán",
                            "Người thắng <b>" + lot + "</b> không thanh toán đúng hạn nên giao dịch đã bị huỷ. Bạn có thể "
                                    + "đề nghị cơ hội thứ hai cho người trả giá cao kế tiếp, hoặc đăng bán lại.",
                            "Mở trang người bán"),
                    new Half("The winner did not pay",
                            "The winner of <b>" + lot + "</b> did not pay in time, so the sale was cancelled. You can "
                                    + "make a second-chance offer to the next bidder, or list it again.",
                            "Open seller tools"));
            case SECOND_CHANCE_OFFER -> build("Cơ hội thứ hai: " + lot, "Second chance: " + lot, siteUrl + "/my-wins",
                    new Half("Lô này vẫn có thể là của bạn",
                            "Người thắng <b>" + lot + "</b> không thanh toán. Bạn có thể mua với giá bạn đã trả, <b>"
                                    + vnd + "</b>, nếu đồng ý trước <b>" + until + "</b>.",
                            "Xem đề nghị"),
                    new Half("This lot can still be yours",
                            "The winner of <b>" + lot + "</b> did not pay. It can be yours for your bid of <b>" + vndEn
                                    + "</b> if you accept before <b>" + until + "</b>.",
                            "View offer"));
            case SELLER_APPROVED -> build("Bạn đã trở thành người bán", "You're now a seller",
                    siteUrl + "/seller/dashboard",
                    new Half("Yêu cầu bán hàng đã được duyệt",
                            "Từ giờ bạn có thể đăng sản phẩm và tạo phiên đấu giá.", "Mở trang người bán"),
                    new Half("Your request to sell was approved",
                            "You can now list products and create auctions.", "Open seller tools"));
            case SELLER_REJECTED -> build("Yêu cầu bán hàng chưa được duyệt", "Your request to sell was not approved",
                    siteUrl + "/become-seller",
                    new Half("Yêu cầu chưa được duyệt",
                            "Lý do: <i>" + reason + "</i><br>Bạn có thể gửi yêu cầu mới bất cứ lúc nào.",
                            "Gửi yêu cầu mới"),
                    new Half("Your request was not approved",
                            "Reason: <i>" + reason + "</i><br>You can send a new request at any time.",
                            "Send a new request"));
            case ORDER_SHIPPED -> build("Đơn hàng đã được gửi: " + lot, "Your order has shipped: " + lot,
                    siteUrl + "/orders",
                    new Half("Hàng đang trên đường tới",
                            "Người bán đã gửi <b>" + lot + "</b>. Khi nhận được hàng, hãy xác nhận và đánh giá người bán." + trackingVi,
                            "Xem đơn hàng"),
                    new Half("It's on its way",
                            "The seller has shipped <b>" + lot + "</b>. Once it arrives, confirm receipt and rate the seller." + trackingEn,
                            "View order"));
        };
    }

    private Rendered build(String subjectVi, String subjectEn, String link, Half vi, Half en) {
        String subject = strip(subjectVi) + " · " + strip(subjectEn);
        String html = """
                <!doctype html>
                <html><body style="margin:0;background:#f4f4f2;font-family:Arial,Helvetica,sans-serif;color:#141414">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:24px 0">
                <tr><td align="center">
                <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                       style="max-width:560px;background:#ffffff;border:1px solid #e2e2de">
                <tr><td style="padding:20px 28px;background:#0b0b0b;color:#d4ff3a;font-weight:bold;letter-spacing:1px">
                NEXBID</td></tr>
                %s
                <tr><td style="padding:0 28px"><hr style="border:none;border-top:1px solid #e2e2de"></td></tr>
                %s
                <tr><td style="padding:20px 28px 28px">
                <a href="%s" style="display:inline-block;background:#141414;color:#ffffff;text-decoration:none;
                   padding:12px 22px;font-weight:bold">%s · %s</a></td></tr>
                <tr><td style="padding:16px 28px;font-size:12px;color:#777;border-top:1px solid #e2e2de">
                Email tự động từ NexBid, vui lòng không trả lời. · An automatic email from NexBid; please do not reply.
                <br>%s
                </td></tr>
                </table></td></tr></table></body></html>
                """.formatted(section(vi), section(en), HtmlUtils.htmlEscape(link), vi.button(), en.button(), FOOTER_SLOT);
        String text = strip(vi.heading()) + "\n\n" + strip(vi.body()) + "\n\n"
                + "----\n\n"
                + strip(en.heading()) + "\n\n" + strip(en.body()) + "\n\n"
                + link + "\n";
        return new Rendered(subject, html, text);
    }

    private static String section(Half half) {
        return """
                <tr><td style="padding:24px 28px 8px"><h2 style="margin:0 0 10px;font-size:20px">%s</h2>
                <p style="margin:0;font-size:15px;line-height:1.55">%s</p></td></tr>
                """.formatted(half.heading(), half.body());
    }

    /** EN: Plain text from the light markup the templates use. / VI: Văn bản thường từ phần markup đơn giản của mẫu. */
    private static String strip(String html) {
        return HtmlUtils.htmlUnescape(html.replace("<br>", "\n").replaceAll("<[^>]+>", ""));
    }

    static String money(BigDecimal amount, Locale locale) {
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(Currency.getInstance("VND"));
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    /** EN: Escapes what users typed before it reaches a template. / VI: Escape thứ người dùng nhập trước khi vào mẫu. */
    static MailFacts escaped(MailFacts facts) {
        return new MailFacts(facts.auctionId(),
                facts.lotTitle() == null ? null : HtmlUtils.htmlEscape(facts.lotTitle()),
                facts.amount(), facts.until(),
                facts.reason() == null ? null : HtmlUtils.htmlEscape(facts.reason()),
                facts.tracking() == null ? null : HtmlUtils.htmlEscape(facts.tracking()));
    }

}
