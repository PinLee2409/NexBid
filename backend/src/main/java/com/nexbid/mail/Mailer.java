package com.nexbid.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import com.nexbid.user.AddressBook;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserService;

/**
 * EN: Queues emails. Called from inside the transaction of the event an email reports, so the email is written only
 *     if that event commits (a caller with no transaction gets one of its own); {@link MailSender} delivers it
 *     afterwards. Nothing here talks to Brevo.
 * VI: Xếp hàng email. Được gọi bên trong transaction của sự kiện mà email báo, nên email chỉ được ghi khi sự kiện đó
 *     commit; {@link MailSender} giao nó sau. Ở đây không gọi Brevo.
 */
@Service
public class Mailer {

    private static final Logger log = LoggerFactory.getLogger(Mailer.class);

    private final MailOutbox outbox;
    private final UserService users;
    private final MailProperties properties;
    private final MailTemplates templates;
    private final AddressBook preferences;
    private final UnsubscribeTokens tokens;

    Mailer(MailOutbox outbox, UserService users, MailProperties properties, AddressBook preferences,
            UnsubscribeTokens tokens) {
        this.outbox = outbox;
        this.users = users;
        this.properties = properties;
        this.templates = new MailTemplates(properties.siteUrl(), properties.assetUrl());
        this.preferences = preferences;
        this.tokens = tokens;
    }

    /** EN: Queues one email to one account. / VI: Xếp hàng một email cho một tài khoản. */
    @Transactional
    public void send(UUID userId, MailKind kind, MailFacts facts) {
        Optional<UserAccount> account = users.findById(userId);
        if (account.isEmpty()) {
            return;
        }
        queue(account.get(), kind, facts, Instant.now());
    }

    /**
     * EN: Outbid emails, throttled: in the lot's final minutes every outbid is emailed — that is when it matters —
     *     and before that at most one per person per lot per throttle window, so a busy lot does not flood an inbox.
     * VI: Email bị vượt giá, có giới hạn: trong những phút cuối của lô thì mọi lần bị vượt giá đều được gửi — đó là
     *     lúc quan trọng — còn trước đó tối đa một email mỗi người mỗi lô trong mỗi khoảng giới hạn, để lô sôi động
     *     không làm ngập hộp thư.
     */
    @Transactional
    public void sendOutbid(UUID userId, MailFacts facts, Instant lotEndsAt, Instant now) {
        if (!preferences.wantsOutbidEmails(userId)) {
            return;
        }
        boolean finalMinutes = !Duration.between(now, lotEndsAt).minus(properties.outbidFinalWindow()).isPositive();
        if (!finalMinutes
                && outbox.sentRecently(userId, facts.auctionId(), MailKind.OUTBID, now.minus(properties.outbidThrottle()))) {
            return;
        }
        users.findById(userId).ifPresent(account -> queue(account, MailKind.OUTBID, facts, now));
    }

    /**
     * EN: The daily digest of new lots, unless the person turned it off. {@code lots} is what fits in one email;
     *     {@code total} counts them all.
     * VI: Email tổng hợp lô mới hằng ngày, trừ khi người đó đã tắt. {@code lots} là phần vừa một email; {@code total}
     *     đếm tất cả.
     */
    @Transactional
    public boolean sendDigest(UUID userId, List<DigestLot> lots, int total) {
        if (lots.isEmpty() || !preferences.wantsDigestEmails(userId)) {
            return false;
        }
        Optional<UserAccount> account = users.findById(userId);
        if (account.isEmpty()) {
            return false;
        }
        String token = tokens.tokenFor(userId);
        String pageLink = siteUrl() + "/unsubscribe?token=" + token + "&list=digest";
        String oneClick = siteUrl() + UnsubscribeController.PATH + "?token=" + token + "&list=digest";
        store(account.get(), MailKind.LOT_DIGEST, null, templates.renderDigest(lots, total, pageLink), Instant.now(),
                oneClick);
        return true;
    }

    private void queue(UserAccount account, MailKind kind, MailFacts facts, Instant now) {
        String name = HtmlUtils.htmlEscape(account.fullName());
        // EN: Only optional emails carry a way out; the others are about the person's own money or goods.
        // VI: Chỉ email có thể tắt mới kèm cách tắt; các email khác là về tiền hoặc hàng của chính người đó.
        String token = kind == MailKind.OUTBID ? tokens.tokenFor(account.id()) : null;
        String pageLink = token == null ? null : siteUrl() + "/unsubscribe?token=" + token;
        String oneClick = token == null ? null : siteUrl() + UnsubscribeController.PATH + "?token=" + token;
        MailTemplates.Rendered mail = templates.render(kind, name, MailTemplates.escaped(facts), pageLink);
        store(account, kind, facts.auctionId(), mail, now, oneClick);
    }

    private void store(UserAccount account, MailKind kind, UUID auctionId, MailTemplates.Rendered mail, Instant now,
            String oneClick) {
        boolean reachable = !reservedDomain(account.email());
        String status = properties.sending() && reachable ? "PENDING" : "SKIPPED";
        outbox.insert(account.id(), account.email(), account.fullName(), kind, auctionId, mail, status, now,
                oneClick);
        if (!properties.sending()) {
            log.info("Email not sent (no NEXBID_BREVO_API_KEY): {} to {} — {}", kind, account.email(), mail.subject());
        } else if (!reachable) {
            log.info("Email not sent (reserved test domain): {} to {} — {}", kind, account.email(), mail.subject());
        }
    }

    /**
     * EN: Seed and test accounts live under .test, a domain that can never receive mail (RFC 2606). Sending there only
     *     bounces, and bounces hurt the sender's standing with Brevo.
     * VI: Tài khoản seed và test nằm dưới .test, tên miền không bao giờ nhận được mail (RFC 2606). Gửi tới đó chỉ bị trả
     *     về, mà mail bị trả về làm giảm uy tín người gửi trên Brevo.
     */
    static boolean reservedDomain(String email) {
        String lower = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".test") || lower.endsWith(".invalid") || lower.endsWith(".example")
                || lower.endsWith(".localhost");
    }

    private String siteUrl() {
        String url = properties.siteUrl();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
