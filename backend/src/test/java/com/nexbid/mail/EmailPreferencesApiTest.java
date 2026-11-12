package com.nexbid.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import com.nexbid.support.TestInfrastructure;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Outbid emails can be turned off — from the profile, from the link in the email, or from the mail app's own
 *     one-click button — and a forged link turns off nothing. Emails about money and goods cannot be turned off.
 * VI: Email bị vượt giá có thể tắt — từ hồ sơ, từ link trong email, hoặc từ nút một chạm của ứng dụng mail — và link
 *     giả không tắt được gì. Email về tiền và hàng thì không tắt được.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class EmailPreferencesApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Mailer mailer;

    @Autowired
    private UnsubscribeTokens tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate tx;

    @Autowired
    private ObjectMapper json;

    private record Person(UUID id, String token) {
    }

    private Person person(String name) throws Exception {
        String email = name.toLowerCase().replace(' ', '.') + "." + UUID.randomUUID() + "@nexbid.com";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"supersecret\"}".formatted(name, email)));
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"supersecret\"}".formatted(email)))
                .andReturn().getResponse().getContentAsString();
        var data = json.readTree(body).get("data");
        return new Person(UUID.fromString(data.get("user").get("id").asString()), data.get("accessToken").asString());
    }

    private void outbid(Person person, UUID lot) {
        // EN: Final minutes, so the throttle never hides the result. / VI: Những phút cuối, để giới hạn không che kết quả.
        Instant now = Instant.now();
        tx.executeWithoutResult(status -> mailer.sendOutbid(person.id(),
                MailFacts.lot(lot, "Lot", BigDecimal.TEN), now.plus(Duration.ofMinutes(5)), now));
    }

    private int outbidEmails(Person person) {
        return jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE user_id = ? AND kind = 'OUTBID'",
                Integer.class, person.id());
    }

    @Test
    void theProfileSwitchStopsOutbidEmails() throws Exception {
        Person person = person("Switch Person");
        mockMvc.perform(get("/api/users/me/email-preferences").header("Authorization", "Bearer " + person.token()))
                .andExpect(jsonPath("$.data.outbid").value(true));

        outbid(person, UUID.randomUUID());
        assertThat(outbidEmails(person)).isEqualTo(1);

        mockMvc.perform(put("/api/users/me/email-preferences").header("Authorization", "Bearer " + person.token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outbid\":false}"))
                .andExpect(status().isOk());
        outbid(person, UUID.randomUUID());
        assertThat(outbidEmails(person)).isEqualTo(1);

        // EN: Receipts still go out. / VI: Biên nhận vẫn được gửi.
        tx.executeWithoutResult(status -> mailer.send(person.id(), MailKind.PAYMENT_RECEIPT,
                MailFacts.lot(UUID.randomUUID(), "Lot", BigDecimal.TEN)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_outbox WHERE user_id = ? AND kind = 'PAYMENT_RECEIPT'",
                Integer.class, person.id())).isEqualTo(1);
    }

    @Test
    void anOutbidEmailCarriesASignedWayOutThatWorksWithoutSigningIn() throws Exception {
        Person person = person("Link Person");
        outbid(person, UUID.randomUUID());

        String html = jdbc.queryForObject("SELECT html FROM email_outbox WHERE user_id = ? AND kind = 'OUTBID'",
                String.class, person.id());
        String header = jdbc.queryForObject("SELECT unsubscribe_url FROM email_outbox WHERE user_id = ? AND kind = 'OUTBID'",
                String.class, person.id());
        String token = tokens.tokenFor(person.id());
        assertThat(html).contains("/unsubscribe?token=" + token);
        assertThat(header).endsWith("/api/email/unsubscribe?token=" + token);

        // EN: Someone else's id with this token's signature, or no signature at all: refused. / VI: Id người khác với chữ ký này, hoặc không có chữ ký: bị từ chối.
        String forged = UUID.randomUUID() + token.substring(token.indexOf('.'));
        mockMvc.perform(post("/api/email/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + forged + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/email/unsubscribe").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + person.id() + "\"}"))
                .andExpect(status().isBadRequest());

        // EN: The mail app's one-click button: a form post to the header's URL (RFC 8058). / VI: Nút một chạm của ứng dụng mail: form post tới URL trong header (RFC 8058).
        mockMvc.perform(post("/api/email/unsubscribe").param("token", token)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("List-Unsubscribe=One-Click"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.outbid").value(false));

        outbid(person, UUID.randomUUID());
        assertThat(outbidEmails(person)).isEqualTo(1);
    }

    @Test
    void emailsAboutMoneyHaveNoUnsubscribeLink() throws Exception {
        Person person = person("Receipt Person");
        tx.executeWithoutResult(status -> mailer.send(person.id(), MailKind.AUCTION_WON,
                MailFacts.lot(UUID.randomUUID(), "Lot", BigDecimal.TEN).withUntil(Instant.now())));

        assertThat(jdbc.queryForObject("SELECT unsubscribe_url FROM email_outbox WHERE user_id = ? AND kind = 'AUCTION_WON'",
                String.class, person.id())).isNull();
        assertThat(jdbc.queryForObject("SELECT html FROM email_outbox WHERE user_id = ? AND kind = 'AUCTION_WON'",
                String.class, person.id())).doesNotContain("/unsubscribe").contains("/profile#email");
    }
}
