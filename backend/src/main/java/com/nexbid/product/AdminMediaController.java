package com.nexbid.product;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * EN: The photo and video review queue. Under /api/admin/media, open to ADMIN and MODERATOR (SecurityConfig).
 * VI: Hàng chờ duyệt ảnh và video. Nằm dưới /api/admin/media, mở cho ADMIN và MODERATOR (SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/media")
public class AdminMediaController {

    private final MediaReviews reviews;

    AdminMediaController(MediaReviews reviews) {
        this.reviews = reviews;
    }

    public record RemoveRequest(@Size(max = 300) String reason) {
    }

    @GetMapping
    public ApiResponse<List<MediaReviews.Item>> queue() {
        return ApiResponse.of(reviews.queue());
    }

    @PostMapping("/{kind}/{id}/keep")
    public ApiResponse<Void> keep(@PathVariable @Pattern(regexp = "IMAGE|VIDEO") String kind, @PathVariable UUID id) {
        reviews.keep(kind, id);
        return ApiResponse.of(null, "Kept");
    }

    @PostMapping("/{kind}/{id}/remove")
    public ApiResponse<Void> remove(@AuthenticationPrincipal CurrentUser admin,
            @PathVariable @Pattern(regexp = "IMAGE|VIDEO") String kind, @PathVariable UUID id,
            @Valid @RequestBody(required = false) RemoveRequest request) {
        reviews.remove(admin.id(), kind, id, request == null ? null : request.reason());
        return ApiResponse.of(null, "Removed");
    }
}
