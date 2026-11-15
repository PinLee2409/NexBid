package com.nexbid.auth;

import java.time.Instant;
import java.util.List;
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
 * EN: Turning two-step sign-in on and off from the profile. Signed in only.
 * VI: Bật và tắt đăng nhập hai bước từ trang hồ sơ. Chỉ khi đã đăng nhập.
 */
@RestController
@RequestMapping("/api/users/me/2fa")
public class TwoFactorController {

    private final TwoFactorService twoFactor;

    public TwoFactorController(TwoFactorService twoFactor) {
        this.twoFactor = twoFactor;
    }

    public record CodeRequest(@NotBlank @Size(max = 20) String code) {
    }

    @GetMapping
    public ApiResponse<Map<String, Boolean>> status(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(Map.of("enabled", twoFactor.enabled(me.id())));
    }

    @PostMapping("/setup")
    public ApiResponse<TwoFactorService.Setup> setup(@AuthenticationPrincipal CurrentUser me) {
        return ApiResponse.of(twoFactor.setup(me.id(), me.email()));
    }

    /** EN: Returns the recovery codes, shown this once. / VI: Trả về các mã khôi phục, chỉ hiện lần này. */
    @PostMapping("/enable")
    public ApiResponse<Map<String, List<String>>> enable(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody CodeRequest request) {
        return ApiResponse.of(Map.of("recoveryCodes", twoFactor.enable(me.id(), request.code(), Instant.now())),
                "Two-step sign-in is on");
    }

    @PostMapping("/disable")
    public ApiResponse<Map<String, Boolean>> disable(@AuthenticationPrincipal CurrentUser me,
            @Valid @RequestBody CodeRequest request) {
        twoFactor.disable(me.id(), request.code(), Instant.now());
        return ApiResponse.of(Map.of("enabled", false), "Two-step sign-in is off");
    }
}
