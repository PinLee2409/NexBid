package com.nexbid.mail;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.user.AddressBook;

/**
 * EN: Turns off outbid emails from a link in one, without signing in. Two shapes: the site's own page posts JSON, and a
 *     mail app's one-click "Unsubscribe" button posts a form to the List-Unsubscribe URL, token in the query (RFC 8058).
 * VI: Tắt email bị vượt giá từ link trong chính email đó, không cần đăng nhập. Hai dạng: trang của site gửi JSON, còn
 *     nút "Huỷ đăng ký" một chạm của ứng dụng mail gửi form tới URL List-Unsubscribe, token nằm trên query (RFC 8058).
 */
@RestController
public class UnsubscribeController {

    static final String PATH = "/api/email/unsubscribe";

    private final UnsubscribeTokens tokens;
    private final AddressBook addresses;

    UnsubscribeController(UnsubscribeTokens tokens, AddressBook addresses) {
        this.tokens = tokens;
        this.addresses = addresses;
    }

    public record UnsubscribeRequest(String token) {
    }

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Boolean>> fromPage(@RequestBody UnsubscribeRequest request) {
        return turnOff(request.token());
    }

    @PostMapping(path = PATH, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ApiResponse<Map<String, Boolean>> oneClick(@RequestParam("token") String token) {
        return turnOff(token);
    }

    private ApiResponse<Map<String, Boolean>> turnOff(String token) {
        UUID userId = tokens.userOf(token).orElseThrow(() ->
                new BusinessException(ErrorCode.VALIDATION_ERROR, "This unsubscribe link is not valid"));
        addresses.setOutbidEmails(userId, false);
        return ApiResponse.of(Map.of("outbid", false), "Outbid emails turned off");
    }
}
