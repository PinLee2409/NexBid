package com.nexbid.message;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: The caller's own conversations (signed in); an admin reads any order's under /api/admin/**.
 * VI: Các cuộc trò chuyện của chính người gọi (cần đăng nhập); admin đọc của mọi đơn dưới /api/admin/**.
 */
@RestController
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    public record SendRequest(@NotBlank @Size(max = 1000) String body) {
    }

    @GetMapping("/api/messages")
    public ApiResponse<List<MessageService.ThreadView>> threads(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(messages.threads(me.id()));
    }

    @GetMapping("/api/messages/unread-count")
    public ApiResponse<Map<String, Integer>> unread(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(Map.of("unread", messages.unreadCount(me.id())));
    }

    @GetMapping("/api/orders/{orderId}/messages")
    public ApiResponse<MessageService.ThreadDetail> open(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID orderId) {
        return ApiResponse.of(messages.open(me.id(), orderId));
    }

    @PostMapping("/api/orders/{orderId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MessageService.MessageView> send(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID orderId, @Valid @RequestBody SendRequest request) {
        return ApiResponse.of(messages.send(me.id(), orderId, request.body()), "Message sent");
    }

    @GetMapping("/api/admin/orders/{orderId}/messages")
    public ApiResponse<List<MessageService.AdminMessageView>> read(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID orderId) {
        return ApiResponse.of(messages.read(me.id(), orderId));
    }
}
