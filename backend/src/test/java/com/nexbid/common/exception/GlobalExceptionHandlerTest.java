package com.nexbid.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.nexbid.infrastructure.config.SecurityConfig;

/**
 * EN: Whatever goes wrong, the body has the same keys. One test per way of failing.
 * VI: Lỗi kiểu gì thì body cũng cùng một bộ key. Mỗi kiểu lỗi một test.
 */
@WebMvcTest(ProbeController.class)
@Import({ SecurityConfig.class, GlobalExceptionHandler.class })
// EN: Signed in, so each request reaches the controller and fails the way the test intends.
// VI: Đã đăng nhập, để request tới được controller và lỗi đúng kiểu test muốn.
@WithMockUser
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void businessRuleRefusalCarriesItsCodeAndStatus() throws Exception {
        mockMvc.perform(get("/__test/business"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BID_TOO_LOW"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void notFoundUses404() throws Exception {
        mockMvc.perform(get("/__test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUCTION_NOT_FOUND"));
    }

    @Test
    void validationReportsEveryFieldAtOnce() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details.email").exists())
                .andExpect(jsonPath("$.details.amount").exists());
    }

    @Test
    void malformedBodyIsAValidationErrorNotACrash() throws Exception {
        mockMvc.perform(post("/__test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void unexpectedFailureNeverLeaksItsCause() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                // EN: The exception named an internal host; the caller is told none of it.
                // VI: Exception có nhắc tên host nội bộ; client không nhận được chữ nào.
                .andExpect(jsonPath("$.message").value("Something went wrong"));
    }

    @Test
    void successEnvelopeHasTheDocumentedShape() throws Exception {
        mockMvc.perform(get("/__test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.lot").value(7))
                .andExpect(jsonPath("$.message").value("Fetched"));
    }
}
