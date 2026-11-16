package com.nexbid.message;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * EN: Replies a person keeps for the chat ("Shipped today", "Still available"), at most ten, sent with one tap. Only a
 *     shortcut for typing: what is sent goes through the usual message checks.
 * VI: Các câu trả lời một người lưu sẵn cho chat ("Đã gửi hàng hôm nay", "Vẫn còn hàng"), tối đa mười câu, gửi bằng một
 *     chạm. Chỉ là lối tắt để khỏi gõ: tin gửi đi vẫn qua các bước kiểm tra tin nhắn như thường.
 */
@RestController
@RequestMapping("/api/users/me/quick-replies")
public class QuickReplies {

    static final int LIMIT = 10;

    private final JdbcTemplate jdbc;

    QuickReplies(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Reply(UUID id, String body, Instant createdAt) {
    }

    public record ReplyRequest(@NotBlank @Size(max = 300) String body) {
    }

    @GetMapping
    public ApiResponse<List<Reply>> mine(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(list(me.id()));
    }

    @PostMapping
    @Transactional
    public ApiResponse<Reply> add(@AuthenticationPrincipal CurrentUser me, @Valid @RequestBody ReplyRequest request) {
        // EN: Locks the person's rows, so two quick adds cannot both slip under the limit.
        // VI: Khoá các dòng của người đó, để hai lần thêm liền nhau không cùng lọt dưới giới hạn.
        int count = jdbc.queryForList("SELECT id FROM quick_replies WHERE user_id = ? FOR UPDATE", UUID.class, me.id())
                .size();
        if (count >= LIMIT) {
            throw new BusinessException(ErrorCode.QUICK_REPLY_LIMIT, "You can keep at most " + LIMIT + " replies");
        }
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO quick_replies (id, user_id, body, created_at) VALUES (?, ?, ?, ?)", id, me.id(),
                request.body().strip(), now.atOffset(ZoneOffset.UTC));
        return ApiResponse.of(new Reply(id, request.body().strip(), now), "Reply saved");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID id) {
        if (jdbc.update("DELETE FROM quick_replies WHERE id = ? AND user_id = ?", id, me.id()) == 0) {
            throw new ResourceNotFoundException(ErrorCode.QUICK_REPLY_NOT_FOUND, "No reply " + id);
        }
        return ApiResponse.of(null, "Reply deleted");
    }

    private List<Reply> list(UUID userId) {
        return jdbc.query("SELECT id, body, created_at FROM quick_replies WHERE user_id = ? ORDER BY created_at",
                (rs, n) -> new Reply(rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant()),
                userId);
    }
}
