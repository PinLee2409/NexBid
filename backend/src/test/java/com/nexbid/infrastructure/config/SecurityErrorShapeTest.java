package com.nexbid.infrastructure.config;

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

import com.nexbid.health.HealthController;
import com.nexbid.support.WebSliceSecurity;

/**
 * EN: The filter chain answers before any controller runs, so its errors are written by SecurityConfig.
 * VI: Filter chain trả lời trước khi controller chạy, nên lỗi ở đây do SecurityConfig viết ra.
 *
 * <p>EN: This test stops it drifting from GlobalExceptionHandler — same keys, not an empty body.
 * <p>VI: Test này giữ nó không lệch khỏi GlobalExceptionHandler — cùng bộ key, không phải body rỗng.
 */
@WebMvcTest(HealthController.class)
@Import(WebSliceSecurity.class)
class SecurityErrorShapeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousCallIsRefusedAsJson() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_AUTHENTICATED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void theProbeRemainsOpen() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
