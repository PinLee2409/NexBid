package com.nexbid.health;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.config.SecurityConfig;

/**
 * A web slice, not a full context: the probe must be provably reachable
 * <em>through</em> the security filter chain, which is the part that is easy to
 * break later. {@link SecurityConfig} is imported for that reason — without it
 * the slice would test a version of the app that has no security at all.
 */
@WebMvcTest(HealthController.class)
@Import(SecurityConfig.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsUpWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void everythingElseStaysClosed() throws Exception {
        // The probe is an exception to the rule, not the start of a pattern.
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isForbidden());
    }
}
