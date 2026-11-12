package com.nexbid.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * EN: Every email speaks Vietnamese and English, links back to the site, and never lets what a user typed become HTML.
 * VI: Mọi email đều có tiếng Việt và tiếng Anh, có link về site, và không để thứ người dùng nhập trở thành HTML.
 */
class MailTemplatesTest {

    private final MailTemplates templates = new MailTemplates("https://nexbid.example/");

    private static final MailFacts WON = MailFacts.lot(
                    UUID.fromString("11111111-2222-3333-4444-555555555555"), "Rolex <Submariner>", new BigDecimal("18500000"))
            .withUntil(Instant.parse("2026-11-14T05:30:00Z"))
            .withReason("Missing <photos> & receipts");

    @ParameterizedTest
    @EnumSource(MailKind.class)
    void everyKindIsBilingualAndLinksToTheSite(MailKind kind) {
        MailTemplates.Rendered mail = templates.render(kind, "Linh", MailTemplates.escaped(WON));

        // EN: "Vietnamese · English" in the subject. / VI: Tiêu đề dạng "tiếng Việt · tiếng Anh".
        assertThat(mail.subject()).contains(" · ");
        assertThat(mail.html()).contains("https://nexbid.example").doesNotContain("https://nexbid.example//");
        assertThat(mail.html()).contains("NEXBID");
        assertThat(mail.text()).contains("----").doesNotContain("<b>").doesNotContain("<br>");
        // EN: Typed text arrives escaped, never as markup. / VI: Văn bản người dùng nhập tới nơi ở dạng đã escape, không bao giờ là markup.
        assertThat(mail.html()).doesNotContain("<Submariner>").doesNotContain("<photos>");
    }

    @Test
    void aWinGivesThePriceInBothLanguagesTheDeadlineInVietnamTimeAndAPayLink() {
        MailTemplates.Rendered mail = templates.render(MailKind.AUCTION_WON, "Linh", MailTemplates.escaped(WON));

        assertThat(mail.subject()).startsWith("Bạn đã thắng: Rolex <Submariner> · You won: Rolex <Submariner>");
        assertThat(mail.html()).contains("Rolex &lt;Submariner&gt;");
        assertThat(mail.html()).contains("18.500.000").contains("18,500,000");
        // EN: 05:30 UTC is 12:30 in Vietnam. / VI: 05:30 UTC là 12:30 giờ Việt Nam.
        assertThat(mail.text()).contains("12:30 14/11/2026 (GMT+7)");
        assertThat(mail.html()).contains("https://nexbid.example/payments").contains("Thanh toán ngay · Pay now");
    }

    @Test
    void aRejectionQuotesTheReason() {
        MailTemplates.Rendered mail = templates.render(MailKind.SELLER_REJECTED, "Linh", MailTemplates.escaped(WON));

        assertThat(mail.html()).contains("Missing &lt;photos&gt; &amp; receipts");
        assertThat(mail.text()).contains("Missing <photos> & receipts");
    }
}
