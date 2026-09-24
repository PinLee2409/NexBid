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

import com.nexbid.support.WebSliceSecurity;

/**
 * EN: A web slice with SecurityConfig imported, so the probe is proven reachable through the filter chain.
 * VI: Test lát web có import SecurityConfig, để chứng minh probe đi lọt qua filter chain.
 */
@WebMvcTest(HealthController.class)
@Import(WebSliceSecurity.class)
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
        // EN: 401 not 403 — an anonymous caller is told to sign in, not that it would be pointless.
        // VI: 401 chứ không phải 403 — người chưa đăng nhập được bảo hãy đăng nhập, không phải bị cấm.
        mockMvc.perform(get("/api/auctions"))
                .andExpect(status().isUnauthorized());
    }
}
