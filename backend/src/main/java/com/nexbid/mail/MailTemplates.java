package com.nexbid.mail;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
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
    /** EN: Null when no inbox could fetch the logo. / VI: Null khi không hộp thư nào tải được logo. */
    private final String logoUrl;

    MailTemplates(String siteUrl) {
        this(siteUrl, null);
    }

    /**
     * EN: The logo comes from {@code assetUrl}, or else from the site — unless the site is localhost, which a mail
     *     client cannot reach, so the header falls back to the name as text.
     * VI: Logo lấy từ {@code assetUrl}, nếu không thì từ site — trừ khi site là localhost, nơi trình đọc mail không tới
     *     được, nên đầu thư dùng tên dạng chữ.
     */
    MailTemplates(String siteUrl, String assetUrl) {
        this.siteUrl = trimSlash(siteUrl);
        String base = assetUrl != null && !assetUrl.isBlank() ? trimSlash(assetUrl)
                : isLocal(this.siteUrl) ? null : this.siteUrl;
        this.logoUrl = base == null ? null : base + "/nexbid-wordmark.png";
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean isLocal(String url) {
        String host = java.net.URI.create(url).getHost();
        return host == null || host.equals("localhost") || host.startsWith("127.") || host.endsWith(".localhost")
                || host.endsWith(".test");
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
        String footer = "<a href=\"" + HtmlUtils.htmlEscape(settings) + "\" style=\"color:#6b6b65\">Cài đặt email · Email settings</a>"
                + (unsubscribeUrl == null ? "" : " &nbsp;|&nbsp; <a href=\"" + HtmlUtils.htmlEscape(unsubscribeUrl)
                        + "\" style=\"color:#6b6b65\">Tắt email bị vượt giá · Turn off outbid emails</a>");
        String text = body.text() + "\nCài đặt email · Email settings: " + settings + "\n"
                + (unsubscribeUrl == null ? "" : "Tắt email bị vượt giá · Turn off outbid emails: " + unsubscribeUrl + "\n");
        return new Rendered(body.subject(), withFont(body.html().replace(FOOTER_SLOT, footer)), text);
    }

    /**
     * EN: Fonts every mail client has, all with full Vietnamese: a glyph like "ờ" never falls back to another face.
     * VI: Các font mọi trình đọc mail đều có, đều đủ tiếng Việt: chữ như "ờ" không bao giờ bị thay bằng font khác.
     */
    private static final String FONT = "font-family:Arial,'Segoe UI',Roboto,Helvetica,sans-serif;";

    /**
     * EN: The font goes on every element, not just the body: Gmail drops the body's style, and Outlook does not
     *     inherit it into tables — either way, parts of one email would show in different fonts.
     * VI: Font được đặt trên mọi phần tử, không chỉ body: Gmail bỏ style của body, còn Outlook không kế thừa nó vào
     *     bảng — kiểu nào thì các phần của cùng một email cũng sẽ hiện bằng font khác nhau.
     */
    private static String withFont(String html) {
        return html.replace("style=\"", "style=\"" + FONT).replace("<td>", "<td style=\"" + FONT + "\">");
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
            case WELCOME -> build(kind, facts, "Chào mừng bạn đến với NexBid", "Welcome to NexBid", siteUrl + "/auctions",
                    new Half("Chào mừng, " + name + "!",
                            "Tài khoản của bạn đã sẵn sàng. Hãy xem các phiên đang diễn ra và đặt lượt trả giá đầu tiên.",
                            "Xem các phiên"),
                    new Half("Welcome, " + name + "!",
                            "Your account is ready. Browse the live lots and place your first bid.",
                            "Browse lots"));
            case OUTBID -> build(kind, facts, "Bạn đã bị trả giá cao hơn: " + lot, "You've been outbid: " + lot, lotLink,
                    new Half("Có người trả giá cao hơn bạn",
                            "Giá hiện tại của <b>" + lot + "</b> là <b>" + vnd + "</b>. Trả giá lại để giữ vị trí dẫn đầu.",
                            "Trả giá lại"),
                    new Half("Someone bid higher",
                            "<b>" + lot + "</b> is now at <b>" + vndEn + "</b>. Bid again to take back the lead.",
                            "Bid again"));
            case AUCTION_WON -> build(kind, facts, "Bạn đã thắng: " + lot, "You won: " + lot, siteUrl + "/payments",
                    new Half("Chúc mừng, bạn đã thắng!",
                            "Bạn thắng <b>" + lot + "</b> với giá <b>" + vnd + "</b>. Vui lòng thanh toán trước <b>"
                                    + until + "</b>, nếu không giao dịch sẽ bị huỷ.",
                            "Thanh toán ngay"),
                    new Half("Congratulations, you won!",
                            "You won <b>" + lot + "</b> for <b>" + vndEn + "</b>. Please pay before <b>" + until
                                    + "</b>, or the sale will be cancelled.",
                            "Pay now"));
            case PAYMENT_REMINDER -> build(kind, facts, "Sắp hết hạn thanh toán: " + lot, "Payment due soon: " + lot,
                    siteUrl + "/payments",
                    new Half("Nhắc thanh toán",
                            "Khoản <b>" + vnd + "</b> cho <b>" + lot + "</b> cần được thanh toán trước <b>" + until + "</b>.",
                            "Thanh toán ngay"),
                    new Half("Payment reminder",
                            "Your payment of <b>" + vndEn + "</b> for <b>" + lot + "</b> is due before <b>" + until + "</b>.",
                            "Pay now"));
            case PAYMENT_RECEIPT -> build(kind, facts, "Đã nhận thanh toán: " + lot, "Payment received: " + lot, siteUrl + "/orders",
                    new Half("Thanh toán thành công",
                            "Chúng tôi đã nhận <b>" + vnd + "</b> cho <b>" + lot
                                    + "</b>. Người bán sẽ gửi hàng sớm; bạn có thể theo dõi đơn hàng bất cứ lúc nào.",
                            "Xem đơn hàng"),
                    new Half("Payment successful",
                            "We received <b>" + vndEn + "</b> for <b>" + lot
                                    + "</b>. The seller will ship soon; you can follow your order at any time.",
                            "View order"));
            case PAYMENT_EXPIRED -> build(kind, facts, "Đã quá hạn thanh toán: " + lot, "Payment window closed: " + lot, lotLink,
                    new Half("Giao dịch đã bị huỷ",
                            "<b>" + lot + "</b> không được thanh toán đúng hạn nên giao dịch đã bị huỷ.",
                            "Xem lô"),
                    new Half("The sale was cancelled",
                            "<b>" + lot + "</b> was not paid in time, so the sale was cancelled.",
                            "View lot"));
            case SALE_CANCELLED -> build(kind, facts, "Giao dịch bị huỷ: " + lot, "Sale cancelled: " + lot,
                    siteUrl + "/seller/auctions",
                    new Half("Người thắng không thanh toán",
                            "Người thắng <b>" + lot + "</b> không thanh toán đúng hạn nên giao dịch đã bị huỷ. Bạn có thể "
                                    + "đề nghị cơ hội thứ hai cho người trả giá cao kế tiếp, hoặc đăng bán lại.",
                            "Mở trang người bán"),
                    new Half("The winner did not pay",
                            "The winner of <b>" + lot + "</b> did not pay in time, so the sale was cancelled. You can "
                                    + "make a second-chance offer to the next bidder, or list it again.",
                            "Open seller tools"));
            case SECOND_CHANCE_OFFER -> build(kind, facts, "Cơ hội thứ hai: " + lot, "Second chance: " + lot, siteUrl + "/my-wins",
                    new Half("Lô này vẫn có thể là của bạn",
                            "Người thắng <b>" + lot + "</b> không thanh toán. Bạn có thể mua với giá bạn đã trả, <b>"
                                    + vnd + "</b>, nếu đồng ý trước <b>" + until + "</b>.",
                            "Xem đề nghị"),
                    new Half("This lot can still be yours",
                            "The winner of <b>" + lot + "</b> did not pay. It can be yours for your bid of <b>" + vndEn
                                    + "</b> if you accept before <b>" + until + "</b>.",
                            "View offer"));
            case SELLER_APPROVED -> build(kind, facts, "Bạn đã trở thành người bán", "You're now a seller",
                    siteUrl + "/seller/dashboard",
                    new Half("Yêu cầu bán hàng đã được duyệt",
                            "Từ giờ bạn có thể đăng sản phẩm và tạo phiên đấu giá.", "Mở trang người bán"),
                    new Half("Your request to sell was approved",
                            "You can now list products and create auctions.", "Open seller tools"));
            case SELLER_REJECTED -> build(kind, facts, "Yêu cầu bán hàng chưa được duyệt", "Your request to sell was not approved",
                    siteUrl + "/become-seller",
                    new Half("Yêu cầu chưa được duyệt",
                            "Lý do: <i>" + reason + "</i><br>Bạn có thể gửi yêu cầu mới bất cứ lúc nào.",
                            "Gửi yêu cầu mới"),
                    new Half("Your request was not approved",
                            "Reason: <i>" + reason + "</i><br>You can send a new request at any time.",
                            "Send a new request"));
            case ORDER_SHIPPED -> build(kind, facts, "Đơn hàng đã được gửi: " + lot, "Your order has shipped: " + lot,
                    siteUrl + "/orders",
                    new Half("Hàng đang trên đường tới",
                            "Người bán đã gửi <b>" + lot + "</b>. Khi nhận được hàng, hãy xác nhận và đánh giá người bán." + trackingVi,
                            "Xem đơn hàng"),
                    new Half("It's on its way",
                            "The seller has shipped <b>" + lot + "</b>. Once it arrives, confirm receipt and rate the seller." + trackingEn,
                            "View order"));
            case RESERVE_PENDING -> build(kind, facts, "Chờ người bán quyết định: " + lot,
                    "Waiting for the seller: " + lot, siteUrl + "/my-wins",
                    new Half("Giá của bạn chưa đạt giá sàn",
                            "Bạn trả giá cao nhất cho <b>" + lot + "</b>, nhưng thấp hơn giá sàn người bán đặt. Người bán "
                                    + "có thể vẫn bán cho bạn với giá này; bạn sẽ nhận được email khi họ quyết định.",
                            "Xem phiên của tôi"),
                    new Half("Your bid is below the reserve",
                            "You were the highest bidder on <b>" + lot + "</b>, but below the seller's reserve. The "
                                    + "seller may still sell it to you at this price; we'll email you when they decide.",
                            "View my lots"));
            case RESERVE_REFUSED -> build(kind, facts, "Lô không được bán: " + lot, "Not sold: " + lot, siteUrl + "/auctions",
                    new Half("Người bán không chấp nhận giá",
                            "<b>" + lot + "</b> sẽ không được bán với giá bạn trả. Bạn không mất khoản nào. Còn rất "
                                    + "nhiều phiên khác đang diễn ra.",
                            "Xem các phiên"),
                    new Half("The seller did not accept",
                            "<b>" + lot + "</b> won't be sold at your bid, and you owe nothing. Plenty of other lots "
                                    + "are live right now.",
                            "Browse lots"));
            case PAYMENT_REFUNDED -> build(kind, facts, "Đã hoàn tiền: " + lot, "Refund issued: " + lot, siteUrl + "/payments",
                    new Half("Bạn đã được hoàn tiền",
                            "NexBid đã chuyển trả <b>" + vnd + "</b> cho <b>" + lot + "</b> vào tài khoản ngân hàng của "
                                    + "bạn, và đơn hàng đã huỷ." + (reason.isEmpty() ? "" : " Mã giao dịch: <b>" + reason
                                    + "</b>.") + " Tiền thường về trong ngày làm việc.",
                            "Xem thanh toán"),
                    new Half("Your money is on its way back",
                            "NexBid transferred <b>" + vndEn + "</b> for <b>" + lot + "</b> back to your bank account, "
                                    + "and the order is cancelled." + (reason.isEmpty() ? "" : " Reference: <b>" + reason
                                    + "</b>.") + " It usually arrives within a working day.",
                            "View payments"));
            case LOT_SOLD -> build(kind, facts, "Lô đã bán: " + lot, "Sold: " + lot, siteUrl + "/seller/orders",
                    new Half("Lô của bạn đã có người mua",
                            "<b>" + lot + "</b> đã bán với giá <b>" + vnd + "</b>. Người mua có tới <b>" + until
                                    + "</b> để thanh toán; chúng tôi sẽ báo bạn ngay khi tiền về.",
                            "Xem đơn bán"),
                    new Half("Your lot has a buyer",
                            "<b>" + lot + "</b> sold for <b>" + vndEn + "</b>. The buyer has until <b>" + until
                                    + "</b> to pay; we'll tell you as soon as they do.",
                            "View sales"));
            case SELLER_PAID -> build(kind, facts, "Người mua đã thanh toán, hãy gửi hàng: " + lot, "Paid, ready to ship: " + lot,
                    siteUrl + "/seller/orders",
                    new Half("Đã nhận tiền, hãy gửi hàng",
                            "Người mua đã thanh toán <b>" + vnd + "</b> cho <b>" + lot + "</b>. Địa chỉ giao hàng có "
                                    + "trong đơn bán; khi gửi xong, nhập mã vận đơn để người mua theo dõi.",
                            "Gửi hàng"),
                    new Half("Paid, time to ship",
                            "The buyer paid <b>" + vndEn + "</b> for <b>" + lot + "</b>. The delivery address is on the "
                                    + "order; once it's sent, add the tracking number so the buyer can follow it.",
                            "Ship it"));
            case RESERVE_DECISION -> build(kind, facts, "Cần bạn quyết định: " + lot, "Your decision: " + lot,
                    siteUrl + "/seller/auctions",
                    new Half("Giá cao nhất thấp hơn giá sàn",
                            "Phiên <b>" + lot + "</b> kết thúc với giá cao nhất <b>" + vnd + "</b>, thấp hơn giá sàn của "
                                    + "bạn. Bạn có thể vẫn bán với giá này trước <b>" + until
                                    + "</b>; quá hạn thì lô không được bán.",
                            "Quyết định"),
                    new Half("The best bid is below your reserve",
                            "<b>" + lot + "</b> closed at <b>" + vndEn + "</b>, below your reserve. You can still sell "
                                    + "at this price before <b>" + until + "</b>; after that the lot goes unsold.",
                            "Decide"));
            case OFFER_ACCEPTED -> build(kind, facts, "Người thứ hai đã đồng ý: " + lot, "Offer accepted: " + lot,
                    siteUrl + "/seller/orders",
                    new Half("Đề nghị cơ hội thứ hai đã được chấp nhận",
                            "Người trả giá kế tiếp đồng ý mua <b>" + lot + "</b> với giá <b>" + vnd
                                    + "</b>. Khoản thanh toán của họ đã mở; chúng tôi sẽ báo bạn khi tiền về.",
                            "Xem đơn bán"),
                    new Half("Your second-chance offer was accepted",
                            "The next bidder agreed to buy <b>" + lot + "</b> for <b>" + vndEn
                                    + "</b>. Their payment is open; we'll tell you once it's paid.",
                            "View sales"));
            case OFFER_DECLINED -> build(kind, facts, "Đề nghị không được nhận: " + lot, "Offer not taken: " + lot,
                    siteUrl + "/seller/products",
                    new Half("Người thứ hai không mua",
                            "Người trả giá kế tiếp đã từ chối hoặc không trả lời kịp đề nghị cho <b>" + lot
                                    + "</b>. Sản phẩm vẫn là của bạn, có thể đăng bán lại bất cứ lúc nào.",
                            "Đăng bán lại"),
                    new Half("The next bidder passed",
                            "The next bidder declined, or did not answer in time, your offer for <b>" + lot
                                    + "</b>. The product is yours to list again whenever you like.",
                            "List it again"));
            case DISPUTE_OPENED -> build(kind, facts, "Người mua khiếu nại: " + lot, "Dispute opened: " + lot,
                    siteUrl + "/seller/orders",
                    new Half("Người mua báo có vấn đề với đơn hàng",
                            "Người mua <b>" + lot + "</b> đã gửi khiếu nại: <i>" + reason + "</i><br>Đơn tạm dừng cho "
                                    + "tới khi NexBid xem xét. Nếu bạn có bằng chứng gửi hàng, hãy giữ lại; chúng tôi có "
                                    + "thể liên hệ bạn.",
                            "Xem đơn bán"),
                    new Half("The buyer reported a problem",
                            "The buyer of <b>" + lot + "</b> opened a dispute: <i>" + reason + "</i><br>The order is "
                                    + "on hold while NexBid looks into it. Keep any proof of shipping; we may contact you.",
                            "View sales"));
            case DISPUTE_REJECTED -> build(kind, facts, "Khiếu nại đã được xem xét: " + lot, "Dispute reviewed: " + lot,
                    siteUrl + "/orders",
                    new Half("Khiếu nại không được chấp nhận",
                            "NexBid đã xem xét khiếu nại về <b>" + lot + "</b> và giữ nguyên giao dịch."
                                    + (reason.isEmpty() ? "" : "<br>Ghi chú: <i>" + reason + "</i>"),
                            "Xem đơn hàng"),
                    new Half("Your dispute was not upheld",
                            "NexBid reviewed your dispute about <b>" + lot + "</b> and the sale stands."
                                    + (reason.isEmpty() ? "" : "<br>Note: <i>" + reason + "</i>"),
                            "View order"));
            case DISPUTE_SELLER_REFUNDED -> build(kind, facts, "Đơn đã hoàn tiền cho người mua: " + lot,
                    "Buyer refunded: " + lot, siteUrl + "/seller/orders",
                    new Half("Người mua đã được hoàn tiền",
                            "Sau khi xem xét khiếu nại, NexBid đã hoàn tiền cho người mua <b>" + lot
                                    + "</b>. Đơn bị huỷ và không được tính vào thu nhập của bạn."
                                    + (reason.isEmpty() ? "" : "<br>Ghi chú: <i>" + reason + "</i>"),
                            "Xem đơn bán"),
                    new Half("The buyer was refunded",
                            "After reviewing the dispute, NexBid refunded the buyer of <b>" + lot + "</b>. The order is cancelled and does not count toward your earnings."
                                    + (reason.isEmpty() ? "" : "<br>Note: <i>" + reason + "</i>"),
                            "View sales"));
            case DISPUTE_SELLER_REJECTED -> build(kind, facts, "Khiếu nại đã đóng, giao dịch giữ nguyên: " + lot,
                    "Dispute closed, sale stands: " + lot, siteUrl + "/seller/earnings",
                    new Half("Giao dịch được giữ nguyên",
                            "NexBid đã bác khiếu nại về <b>" + lot + "</b>. Đơn tiếp tục như bình thường; tiền bán "
                                    + "được tính vào thu nhập của bạn khi đơn hoàn tất."
                                    + (reason.isEmpty() ? "" : "<br>Ghi chú: <i>" + reason + "</i>"),
                            "Xem thu nhập"),
                    new Half("The sale stands",
                            "NexBid turned down the dispute about <b>" + lot + "</b>. The order carries on; the money "
                                    + "counts toward your earnings once it completes."
                                    + (reason.isEmpty() ? "" : "<br>Note: <i>" + reason + "</i>"),
                            "View earnings"));
            case SELLER_PAYOUT -> build(kind, facts, "NexBid đã chuyển tiền cho bạn: " + vnd, "You've been paid: " + vndEn,
                    siteUrl + "/seller/earnings",
                    new Half("Tiền đã được chuyển",
                            "NexBid đã chuyển <b>" + vnd + "</b> vào tài khoản ngân hàng của bạn. Mã giao dịch: <b>"
                                    + reason + "</b>. Tiền thường về trong ngày làm việc.",
                            "Xem thu nhập"),
                    new Half("Your money is on its way",
                            "NexBid transferred <b>" + vndEn + "</b> to your bank account. Reference: <b>" + reason
                                    + "</b>. It usually arrives within a working day.",
                            "View earnings"));
            case ORDER_COMPLETING -> build(kind, facts, "Đơn sắp tự hoàn tất: " + lot, "Completing soon: " + lot,
                    siteUrl + "/orders",
                    new Half("Bạn đã nhận được hàng chưa?",
                            "Đơn <b>" + lot + "</b> sẽ tự hoàn tất lúc <b>" + until + "</b> và tiền được chuyển cho "
                                    + "người bán. Nếu đã nhận đúng hàng, hãy xác nhận và đánh giá người bán; nếu chưa nhận "
                                    + "hoặc có vấn đề, hãy gửi khiếu nại trước thời điểm đó." + trackingVi,
                            "Xem đơn hàng"),
                    new Half("Did your order arrive?",
                            "<b>" + lot + "</b> completes on its own at <b>" + until + "</b> and the money goes to the "
                                    + "seller. If it arrived as described, confirm and rate the seller; if not, open a "
                                    + "dispute before then." + trackingEn,
                            "View order"));
            case NEW_REVIEW -> build(kind, facts, "Đánh giá mới: " + lot, "New review: " + lot, lotLink,
                    new Half("Bạn vừa nhận một đánh giá",
                            "Người mua <b>" + lot + "</b> đã đánh giá bạn <b>" + stars(facts.rating())
                                    + " sao</b>. Đánh giá hiện trên mọi lô của bạn và giúp người mua sau tin tưởng hơn.",
                            "Xem đánh giá"),
                    new Half("You have a new review",
                            "The buyer of <b>" + lot + "</b> rated you <b>" + stars(facts.rating())
                                    + " stars</b>. Reviews show on all your lots and help future buyers trust you.",
                            "See the review"));
        };
    }

    private static String stars(Integer rating) {
        int filled = rating == null ? 0 : Math.max(0, Math.min(5, rating));
        return filled + "/5";
    }

    // EN: The site's own colours, so an email looks like the page it links to.
    // VI: Màu của chính site, để email trông giống trang mà nó dẫn tới.
    private static final String INK = "#0a0a0a";
    private static final String PAPER = "#efefea";
    private static final String LINE = "#e2e2dc";
    private static final String DIM = "#6b6b65";
    private static final String SIGNAL = "#bcfc04";
    private static final String DANGER = "#cc2a1c";

    /** EN: The small label above the heading, and whether it is bad news. / VI: Nhãn nhỏ trên tiêu đề, và đó có phải tin xấu không. */
    private record Tag(String label, boolean warning) {
    }

    private static Tag tag(MailKind kind) {
        return switch (kind) {
            case WELCOME -> new Tag("Chào mừng · Welcome", false);
            case OUTBID -> new Tag("Bị vượt giá · Outbid", true);
            case AUCTION_WON -> new Tag("Thắng phiên · You won", false);
            case PAYMENT_REMINDER -> new Tag("Nhắc thanh toán · Payment due", true);
            case PAYMENT_RECEIPT -> new Tag("Đã thanh toán · Paid", false);
            case PAYMENT_EXPIRED -> new Tag("Quá hạn · Expired", true);
            case SALE_CANCELLED -> new Tag("Giao dịch huỷ · Sale cancelled", true);
            case SECOND_CHANCE_OFFER -> new Tag("Cơ hội thứ hai · Second chance", false);
            case SELLER_APPROVED -> new Tag("Người bán · Seller", false);
            case SELLER_REJECTED -> new Tag("Yêu cầu bán hàng · Seller request", true);
            case ORDER_SHIPPED -> new Tag("Đang giao · Shipped", false);
            case RESERVE_PENDING -> new Tag("Chờ người bán · Pending", false);
            case RESERVE_REFUSED -> new Tag("Không bán · Not sold", true);
            case PAYMENT_REFUNDED -> new Tag("Hoàn tiền · Refund", false);
            case LOT_SOLD -> new Tag("Đã bán · Sold", false);
            case SELLER_PAID -> new Tag("Cần gửi hàng · Ready to ship", false);
            case RESERVE_DECISION -> new Tag("Cần quyết định · Action needed", true);
            case OFFER_ACCEPTED -> new Tag("Cơ hội thứ hai · Accepted", false);
            case OFFER_DECLINED -> new Tag("Cơ hội thứ hai · Declined", true);
            case NEW_REVIEW -> new Tag("Đánh giá mới · New review", false);
            case DISPUTE_OPENED -> new Tag("Khiếu nại · Dispute", true);
            case DISPUTE_REJECTED -> new Tag("Khiếu nại · Dispute", false);
            case DISPUTE_SELLER_REFUNDED -> new Tag("Đã hoàn tiền · Refunded", true);
            case DISPUTE_SELLER_REJECTED -> new Tag("Khiếu nại đã đóng · Closed", false);
            case SELLER_PAYOUT -> new Tag("Đã chuyển tiền · Paid out", false);
            case ORDER_COMPLETING -> new Tag("Sắp hoàn tất · Completing soon", false);
        };
    }

    /** EN: What the amount means in each email. / VI: Số tiền mang nghĩa gì trong từng email. */
    private static String amountLabel(MailKind kind) {
        return switch (kind) {
            case OUTBID -> "Giá hiện tại · Current bid";
            case AUCTION_WON, LOT_SOLD -> "Giá thắng · Winning bid";
            case PAYMENT_RECEIPT, SELLER_PAID -> "Đã thanh toán · Paid";
            case SECOND_CHANCE_OFFER, RESERVE_PENDING, RESERVE_REFUSED -> "Giá của bạn · Your bid";
            case RESERVE_DECISION -> "Giá cao nhất · Highest bid";
            case PAYMENT_REFUNDED -> "Đã hoàn · Refunded";
            case OFFER_ACCEPTED -> "Giá bán · Sale price";
            case DISPUTE_SELLER_REFUNDED -> "Đã hoàn · Refunded";
            case SELLER_PAYOUT -> "Số tiền chuyển · Amount sent";
            default -> "Số tiền · Amount";
        };
    }

    /** EN: The deadline's label, or null where there is nothing to do before one. / VI: Nhãn hạn chót, hoặc null khi không có gì phải làm trước hạn. */
    private static String deadlineLabel(MailKind kind) {
        return switch (kind) {
            case AUCTION_WON, PAYMENT_REMINDER -> "Hạn thanh toán · Pay by";
            case SECOND_CHANCE_OFFER -> "Hạn đồng ý · Accept by";
            case RESERVE_DECISION -> "Hạn quyết định · Decide by";
            case RESERVE_PENDING -> "Người bán quyết định trước · Seller decides by";
            case LOT_SOLD -> "Người mua trả trước · Buyer pays by";
            case ORDER_COMPLETING -> "Khiếu nại trước · Dispute by";
            default -> null;
        };
    }

    /**
     * EN: The lot, the amount, the deadline and the parcel in one card, so the facts stand out from the prose.
     * VI: Lô, số tiền, hạn chót và kiện hàng trong một thẻ, để các thông tin chính nổi bật khỏi phần chữ.
     */
    private static String card(MailKind kind, MailFacts facts) {
        if (kind == MailKind.WELCOME || kind == MailKind.SELLER_APPROVED || kind == MailKind.SELLER_REJECTED) {
            return "";
        }
        List<String[]> rows = new ArrayList<>();
        if (facts.lotTitle() != null) {
            rows.add(new String[] {"Lô · Lot", "<span style=\"font-size:16px;font-weight:bold;color:" + INK + "\">"
                    + facts.lotTitle() + "</span>"});
        }
        if (facts.amount() != null) {
            rows.add(new String[] {amountLabel(kind), "<span style=\"font-size:24px;font-weight:bold;color:" + INK
                    + ";letter-spacing:-0.5px\">" + money(facts.amount(), Locale.forLanguageTag("vi-VN"))
                    + "</span><br><span style=\"font-size:12px;color:" + DIM + "\">"
                    + money(facts.amount(), Locale.US) + "</span>"});
        }
        // EN: A deadline only matters where the person has to act before it. / VI: Hạn chót chỉ có ý nghĩa khi người đó phải làm gì đó trước hạn.
        if (facts.until() != null && deadlineLabel(kind) != null) {
            rows.add(new String[] {deadlineLabel(kind), "<span style=\"font-size:15px;font-weight:bold;color:" + INK
                    + "\">" + WHEN.format(facts.until()) + "</span> <span style=\"font-size:12px;color:" + DIM
                    + "\">GMT+7</span>"});
        }
        if (facts.rating() != null) {
            rows.add(new String[] {"Đánh giá · Rating", "<span style=\"font-size:22px;color:#e8a400;letter-spacing:2px\">"
                    + "★".repeat(Math.max(0, Math.min(5, facts.rating()))) + "<span style=\"color:" + LINE + "\">"
                    + "★".repeat(5 - Math.max(0, Math.min(5, facts.rating()))) + "</span></span> <span style=\"font-size:13px;color:"
                    + DIM + "\">" + facts.rating() + "/5</span>"});
        }
        if (facts.tracking() != null) {
            rows.add(new String[] {kind == MailKind.PAYMENT_REFUNDED ? "Chuyển tới · Sent to" : "Vận chuyển · Shipping", "<span style=\"font-size:15px;font-weight:bold;color:"
                    + INK + "\">" + facts.tracking() + "</span>"});
        }
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder cells = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            String divider = i == rows.size() - 1 ? "" : "border-bottom:1px solid " + LINE + ";";
            cells.append("""
                    <tr><td style="padding:14px 18px;%s">
                    <div style="font-size:11px;letter-spacing:1px;text-transform:uppercase;color:%s;margin-bottom:4px">%s</div>
                    %s</td></tr>
                    """.formatted(divider, DIM, rows.get(i)[0], rows.get(i)[1]));
        }
        return """
                <tr><td style="padding:24px 32px 0">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="background:#f7f7f3;border:1px solid %s;border-radius:10px">%s</table>
                </td></tr>
                """.formatted(LINE, cells);
    }

    /**
     * EN: The wordmark image; its alt text keeps the name readable while images are blocked.
     * VI: Ảnh wordmark; alt text giữ tên đọc được khi ảnh bị chặn.
     */
    private String brand() {
        if (logoUrl != null) {
            return "<img src=\"" + HtmlUtils.htmlEscape(logoUrl) + "\" width=\"132\" height=\"27\" alt=\"NEXBID\""
                    + " style=\"display:block;border:0;width:132px;height:27px;color:#f2f0ea;font-size:18px;"
                    + "font-weight:bold;letter-spacing:4px\">";
        }
        return "<span style=\"font-size:18px;font-weight:bold;letter-spacing:4px;color:#f2f0ea\">"
                + "<span style=\"display:inline-block;width:10px;height:10px;background:" + SIGNAL
                + ";border-radius:2px;margin-right:10px\"></span>NEXBID</span>";
    }

    private Rendered build(MailKind kind, MailFacts facts, String subjectVi, String subjectEn, String link, Half vi,
            Half en) {
        String subject = strip(subjectVi) + " · " + strip(subjectEn);
        Tag tag = tag(kind);
        String preview = HtmlUtils.htmlEscape(strip(vi.body()).replace('\n', ' '));
        String html = """
                <!doctype html>
                <html lang="vi"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <meta name="color-scheme" content="light only"><title>%s</title></head>
                <body style="margin:0;padding:0;background:%s;color:%s">
                <div style="display:none;max-height:0;overflow:hidden;opacity:0">%s</div>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:%s">
                <tr><td align="center" style="padding:32px 12px">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                       style="max-width:560px;background:#ffffff;border:1px solid %s;border-radius:14px;overflow:hidden">
                <tr><td style="background:%s;padding:22px 32px">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr>
                  <td>%s</td>
                  <td align="right" style="font-size:11px;letter-spacing:1px;color:#9a9a93;text-transform:uppercase">Đấu giá trực tuyến</td>
                  </tr></table>
                </td></tr>
                <tr><td style="padding:32px 32px 0">
                  <span style="display:inline-block;padding:6px 12px;border-radius:999px;font-size:11px;font-weight:bold;
                        letter-spacing:1.2px;text-transform:uppercase;background:%s;color:%s">%s</span>
                </td></tr>
                <tr><td style="padding:18px 32px 0">
                  <h1 style="margin:0 0 12px;font-size:26px;line-height:1.25;font-weight:bold;color:%s">%s</h1>
                  <p style="margin:0;font-size:16px;line-height:1.6;color:#2b2b28">%s</p>
                </td></tr>
                %s
                <tr><td style="padding:28px 32px 4px">
                  <table role="presentation" cellpadding="0" cellspacing="0"><tr>
                  <td style="background:%s;border-radius:10px">
                    <a href="%s" style="display:inline-block;padding:15px 28px;font-size:15px;font-weight:bold;
                       color:#ffffff;text-decoration:none">%s · %s &nbsp;<span style="color:%s">&rarr;</span></a>
                  </td></tr></table>
                </td></tr>
                <tr><td style="padding:32px 32px 0">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr>
                  <td style="border-top:1px solid %s;padding-top:22px;font-size:11px;letter-spacing:1.5px;
                      text-transform:uppercase;color:%s">English</td></tr></table>
                </td></tr>
                <tr><td style="padding:10px 32px 36px">
                  <h2 style="margin:0 0 8px;font-size:19px;line-height:1.3;font-weight:bold;color:%s">%s</h2>
                  <p style="margin:0;font-size:14px;line-height:1.6;color:#55554f">%s</p>
                </td></tr>
                </table>
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px">
                <tr><td align="center" style="padding:22px 24px 8px;font-size:12px;line-height:1.7;color:%s">
                  Email tự động từ NexBid, vui lòng không trả lời.<br>An automatic email from NexBid; please do not reply.
                  <br>%s
                </td></tr></table>
                </td></tr></table>
                </body></html>
                """.formatted(
                        HtmlUtils.htmlEscape(strip(subjectVi)), PAPER, INK, preview, PAPER, LINE, INK, brand(),
                        tag.warning() ? DANGER : SIGNAL, tag.warning() ? "#ffffff" : INK, tag.label(),
                        INK, vi.heading(), vi.body(),
                        card(kind, facts),
                        INK, HtmlUtils.htmlEscape(link), vi.button(), en.button(), SIGNAL,
                        LINE, DIM, INK, en.heading(), en.body(),
                        DIM, FOOTER_SLOT);
        String text = strip(vi.heading()) + "\n\n" + strip(vi.body()) + "\n\n"
                + "----\n\n"
                + strip(en.heading()) + "\n\n" + strip(en.body()) + "\n\n"
                + link + "\n";
        return new Rendered(subject, html, text);
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
                facts.tracking() == null ? null : HtmlUtils.htmlEscape(facts.tracking()), facts.rating());
    }

}
