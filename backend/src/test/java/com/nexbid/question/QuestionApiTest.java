package com.nexbid.question;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nexbid.support.TestInfrastructure;
import com.nexbid.user.RoleName;
import com.nexbid.user.UserService;

import tools.jackson.databind.ObjectMapper;

/**
 * EN: Buyers ask in public, the lot's seller answers once, and each side hears of the other.
 * VI: Người mua hỏi công khai, người bán của lô trả lời một lần, và mỗi bên được báo về bên kia.
 */
@SpringBootTest(properties = "nexbid.scheduler.enabled=false")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class QuestionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService users;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private String tokenFor(String email, String name, RoleName role) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"%s","email":"%s","password":"supersecret"}
                """.formatted(name, email)));
        users.grantRole(email, role);
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"supersecret"}
                        """.formatted(email)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("data").get("accessToken").asString();
    }

    private UUID idOf(String email) {
        return users.findByEmail(email).orElseThrow().id();
    }

    /** EN: A live lot of the given seller. / VI: Một lô đang mở của người bán đã cho. */
    private String liveLot(String seller, String admin, String name) throws Exception {
        String categories = mockMvc.perform(get("/api/categories")).andReturn().getResponse().getContentAsString();
        String categoryId = objectMapper.readTree(categories).get("data").get(0).get("id").asString();
        String productBody = mockMvc.perform(post("/api/seller/products").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"name":"%s","description":"A long description of the item.",
                                 "categoryId":"%s","condition":"LIKE_NEW","publishNow":true}
                                """.formatted(name, categoryId)))
                .andReturn().getResponse().getContentAsString();
        String productId = objectMapper.readTree(productBody).get("data").get("id").asString();
        String auctionBody = mockMvc.perform(post("/api/seller/auctions").header("Authorization", "Bearer " + seller)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId":"%s","startingPrice":1000000,"minimumIncrement":100000,
                                 "startTime":"%s","endTime":"%s"}
                                """.formatted(productId, Instant.now().plus(Duration.ofMinutes(30)),
                                Instant.now().plus(Duration.ofHours(4)))))
                .andReturn().getResponse().getContentAsString();
        String auctionId = objectMapper.readTree(auctionBody).get("data").get("id").asString();
        mockMvc.perform(post("/api/seller/auctions/" + auctionId + "/submit").header("Authorization", "Bearer " + seller));
        mockMvc.perform(post("/api/admin/auctions/" + auctionId + "/approve").header("Authorization", "Bearer " + admin));
        jdbc.update("UPDATE auctions SET status = 'ACTIVE', start_time = now() - interval '1 hour' WHERE id = ?::uuid",
                auctionId);
        return auctionId;
    }

    private ResultActions ask(String token, String auctionId, String body) throws Exception {
        var request = post("/api/auctions/" + auctionId + "/questions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"%s\"}".formatted(body));
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions answer(String token, String questionId, String answer) throws Exception {
        return mockMvc.perform(post("/api/seller/questions/" + questionId + "/answer")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"answer\":\"%s\"}".formatted(answer)));
    }

    private String idFrom(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString()).get("data").get("id")
                .asString();
    }

    private List<String> noticesTo(UUID userId) {
        return jdbc.queryForList("SELECT type FROM notifications WHERE user_id = ?", String.class, userId);
    }

    private List<String> mailsTo(UUID userId) {
        return jdbc.queryForList("SELECT kind FROM email_outbox WHERE user_id = ?", String.class, userId);
    }

    @Test
    void aBuyerAsksInPublicAndTheSellerAnswersOnce() throws Exception {
        String seller = tokenFor("qa.seller@nexbid.com", "Quinn Seller", RoleName.SELLER);
        String otherSeller = tokenFor("qa.other@nexbid.com", "Other Seller", RoleName.SELLER);
        String admin = tokenFor("qa.admin@nexbid.com", "Qa Admin", RoleName.ADMIN);
        String buyer = tokenFor("qa.buyer@nexbid.com", "Linh Buyer", RoleName.BUYER);
        String auctionId = liveLot(seller, admin, "Seiko with questions");

        mockMvc.perform(get("/api/auctions/" + auctionId + "/questions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        ask(null, auctionId, "Is the box included?").andExpect(status().isUnauthorized());
        ask(seller, auctionId, "Asking about my own lot").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_ALLOWED"));
        ask(buyer, auctionId, "Box?").andExpect(status().isBadRequest());

        ResultActions asked = ask(buyer, auctionId, "  Is the original box included?  ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.body").value("Is the original box included?"))
                .andExpect(jsonPath("$.data.asker").value("lin***"))
                .andExpect(jsonPath("$.data.answer").doesNotExist());
        String questionId = idFrom(asked);
        assertThat(noticesTo(idOf("qa.seller@nexbid.com"))).contains("NEW_QUESTION");
        assertThat(mailsTo(idOf("qa.seller@nexbid.com"))).contains("LOT_QUESTION");

        // EN: Only the lot's own seller answers; a buyer is not a seller at all. / VI: Chỉ người bán của lô trả lời; người mua thì không phải người bán.
        answer(otherSeller, questionId, "Yes").andExpect(status().isNotFound());
        answer(buyer, questionId, "Yes").andExpect(status().isForbidden());
        answer(seller, questionId, " ").andExpect(status().isBadRequest());
        answer(seller, questionId, "Yes, box and papers.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer").value("Yes, box and papers."))
                .andExpect(jsonPath("$.data.answeredAt").isNotEmpty());
        answer(seller, questionId, "Again").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_ANSWERED"));
        assertThat(noticesTo(idOf("qa.buyer@nexbid.com"))).contains("QUESTION_ANSWERED");
        assertThat(mailsTo(idOf("qa.buyer@nexbid.com"))).contains("QUESTION_ANSWERED");

        // EN: Anyone can read it, signed in or not. / VI: Ai cũng đọc được, đăng nhập hay không.
        mockMvc.perform(get("/api/auctions/" + auctionId + "/questions"))
                .andExpect(jsonPath("$.data[0].body").value("Is the original box included?"))
                .andExpect(jsonPath("$.data[0].answer").value("Yes, box and papers."));
    }

    @Test
    void askingStopsAtThreeWaitingAndWhenTheLotCloses() throws Exception {
        String seller = tokenFor("qa.limit.seller@nexbid.com", "Limit Seller", RoleName.SELLER);
        String admin = tokenFor("qa.limit.admin@nexbid.com", "Limit Admin", RoleName.ADMIN);
        String buyer = tokenFor("qa.limit.buyer@nexbid.com", "Limit Buyer", RoleName.BUYER);
        String other = tokenFor("qa.limit.other@nexbid.com", "Other Buyer", RoleName.BUYER);
        String auctionId = liveLot(seller, admin, "Lot with many questions");

        for (int i = 1; i <= QuestionService.WAITING_LIMIT; i++) {
            ask(buyer, auctionId, "Question number " + i).andExpect(status().isCreated());
        }
        ask(buyer, auctionId, "One question too many").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUESTION_LIMIT"));
        // EN: The limit is per person. / VI: Giới hạn tính theo từng người.
        ask(other, auctionId, "My own first question").andExpect(status().isCreated());

        jdbc.update("UPDATE auctions SET status = 'ENDED' WHERE id = ?::uuid", auctionId);
        ask(other, auctionId, "Is it still available?").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_ALLOWED"));

        jdbc.update("UPDATE auctions SET status = 'DRAFT' WHERE id = ?::uuid", auctionId);
        mockMvc.perform(get("/api/auctions/" + auctionId + "/questions")).andExpect(status().isNotFound());
    }
}
