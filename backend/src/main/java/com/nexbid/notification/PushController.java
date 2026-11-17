package com.nexbid.notification;

import java.util.Map;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Turning browser push on and off for this browser. Signed in only.
 * VI: Bật và tắt thông báo đẩy cho trình duyệt này. Chỉ khi đã đăng nhập.
 */
@RestController
@RequestMapping("/api/users/me/push")
public class PushController {

    private final PushService push;

    public PushController(PushService push) {
        this.push = push;
    }

    public record SubscribeRequest(
            @NotBlank @Size(max = 1000) String endpoint,
            @NotBlank @Size(max = 200) String p256dh,
            @NotBlank @Size(max = 100) String auth,
            @Size(max = 5) String locale) {
    }

    public record EndpointRequest(@NotBlank @Size(max = 1000) String endpoint) {
    }

    /** EN: The key the browser subscribes with. / VI: Khoá trình duyệt dùng để đăng ký. */
    @GetMapping("/key")
    public ApiResponse<Map<String, String>> key() {
        return ApiResponse.of(Map.of("publicKey", push.publicKey()));
    }

    @PostMapping("/status")
    public ApiResponse<Map<String, Boolean>> status(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody EndpointRequest request) {
        return ApiResponse.of(Map.of("subscribed", push.subscribed(me.id(), request.endpoint())));
    }

    @PostMapping
    public ApiResponse<Map<String, Boolean>> subscribe(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody SubscribeRequest request) {
        push.subscribe(me.id(), new PushService.Subscription(request.endpoint(), request.p256dh(), request.auth(),
                request.locale()));
        return ApiResponse.of(Map.of("subscribed", true), "Push notifications are on for this browser");
    }

    @PostMapping("/unsubscribe")
    public ApiResponse<Map<String, Boolean>> unsubscribe(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody EndpointRequest request) {
        push.unsubscribe(me.id(), request.endpoint());
        return ApiResponse.of(Map.of("subscribed", false), "Push notifications are off for this browser");
    }
}
