package com.nexbid.alert;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.alert.dto.SaveSearchRequest;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * EN: The caller's own saved searches and followed sellers; everything here needs a signed-in user.
 * VI: Bộ lọc đã lưu và người bán đang theo dõi của chính người gọi; mọi thứ ở đây cần đăng nhập.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alerts;

    public AlertController(AlertService alerts) {
        this.alerts = alerts;
    }

    @GetMapping("/searches")
    public ApiResponse<List<AlertViews.SavedSearch>> searches(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(alerts.searchesOf(me.id()));
    }

    @PostMapping("/searches")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AlertViews.SavedSearch> save(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody SaveSearchRequest request) {
        return ApiResponse.of(alerts.save(me.id(), request), "Search saved");
    }

    @DeleteMapping("/searches/{searchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal CurrentUser me, @PathVariable UUID searchId) {
        alerts.deleteSearch(me.id(), searchId);
    }

    @GetMapping("/sellers")
    public ApiResponse<List<AlertViews.FollowedSeller>> follows(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(alerts.followsOf(me.id()));
    }

    @GetMapping("/sellers/{sellerId}")
    public ApiResponse<AlertViews.FollowState> followState(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID sellerId) {
        return ApiResponse.of(alerts.followState(me.id(), sellerId));
    }

    @PutMapping("/sellers/{sellerId}")
    public ApiResponse<AlertViews.FollowState> follow(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID sellerId) {
        return ApiResponse.of(alerts.follow(me.id(), sellerId), "Following seller");
    }

    @DeleteMapping("/sellers/{sellerId}")
    public ApiResponse<AlertViews.FollowState> unfollow(@AuthenticationPrincipal CurrentUser me,
            @PathVariable UUID sellerId) {
        return ApiResponse.of(alerts.unfollow(me.id(), sellerId), "Unfollowed seller");
    }
}
