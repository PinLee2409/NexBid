package com.nexbid.user;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.user.dto.SetBlockedRequest;

import jakarta.validation.Valid;

/** EN: The admin action that produces USER_BLOCKED audit entries. / VI: Thao tác admin tạo audit USER_BLOCKED. */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService users;

    AdminUserController(UserService users) {
        this.users = users;
    }

    @PatchMapping("/{id}/block")
    public ApiResponse<UserAccount> setBlocked(
            @AuthenticationPrincipal CurrentUser admin,
            @PathVariable UUID id,
            @Valid @RequestBody SetBlockedRequest request) {
        return ApiResponse.of(users.setBlocked(id, admin.id(), request.blocked()),
                request.blocked() ? "Account blocked" : "Account unblocked");
    }
}
