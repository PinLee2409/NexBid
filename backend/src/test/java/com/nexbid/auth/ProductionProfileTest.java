package com.nexbid.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.support.TestInfrastructure;

/**
 * EN: The `prod` profile with a proper secret: it starts, keeps the API map private, and answers with the
 *     browser safety headers.
 * VI: Profile `prod` với secret đúng chuẩn: khởi động được, giữ kín bản đồ API, và trả kèm các header an toàn cho
 *     trình duyệt.
 */
@SpringBootTest(properties = {
        "nexbid.scheduler.enabled=false",
        "nexbid.jwt.secret=c2f1b7e04a9d6e3f8b2c5a7d1e9f0b3c4d6a8e2f1b7c9d0e"
})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
class ProductionProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void theApiDocsAreNotPublished() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void answersCarryTheBrowserSafetyHeaders() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
