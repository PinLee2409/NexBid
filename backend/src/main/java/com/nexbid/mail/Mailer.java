package com.nexbid.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

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

    Mailer(MailOutbox outbox, UserService users, MailProperties properties) {
        this.outbox = outbox;
        this.users = users;
        this.properties = properties;
        this.templates = new MailTemplates(properties.siteUrl());
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
        boolean finalMinutes = !Duration.between(now, lotEndsAt).minus(properties.outbidFinalWindow()).isPositive();
        if (!finalMinutes
                && outbox.sentRecently(userId, facts.auctionId(), MailKind.OUTBID, now.minus(properties.outbidThrottle()))) {
            return;
        }
        users.findById(userId).ifPresent(account -> queue(account, MailKind.OUTBID, facts, now));
    }

    private void queue(UserAccount account, MailKind kind, MailFacts facts, Instant now) {
        String name = HtmlUtils.htmlEscape(account.fullName());
        MailTemplates.Rendered mail = templates.render(kind, name, MailTemplates.escaped(facts));
        String status = properties.sending() ? "PENDING" : "SKIPPED";
        outbox.insert(account.id(), account.email(), account.fullName(), kind, facts.auctionId(), mail, status, now);
        if (!properties.sending()) {
            log.info("Email not sent (no NEXBID_BREVO_API_KEY): {} to {} — {}", kind, account.email(), mail.subject());
        }
    }
}
