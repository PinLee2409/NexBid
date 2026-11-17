package com.nexbid.user;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * EN: Who works on NexBid and with which role. ADMIN only (SecurityConfig), since it hands out access.
 * VI: Ai làm việc trên NexBid và với vai trò nào. Chỉ ADMIN (SecurityConfig), vì nó cấp quyền truy cập.
 */
@RestController
@RequestMapping("/api/admin/staff")
public class AdminStaffController {

    private final UserService users;

    AdminStaffController(UserService users) {
        this.users = users;
    }

    public record StaffRolesRequest(@NotNull Set<RoleName> roles) {
    }

    public record AddStaffRequest(@NotBlank String email, @NotNull Set<RoleName> roles) {
    }

    @GetMapping
    public ApiResponse<List<UserAccount>> list() {
        return ApiResponse.of(users.staff());
    }

    @PutMapping("/{id}")
    public ApiResponse<UserAccount> setRoles(@AuthenticationPrincipal CurrentUser admin, @PathVariable UUID id,
            @Valid @RequestBody StaffRolesRequest request) {
        return ApiResponse.of(users.setStaffRoles(id, admin.id(), request.roles()), "Roles saved");
    }

    @PostMapping
    public ApiResponse<UserAccount> add(@AuthenticationPrincipal CurrentUser admin,
            @Valid @RequestBody AddStaffRequest request) {
        UserAccount account = users.findByEmail(request.email().trim())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND,
                        "No account for " + request.email().trim()));
        return ApiResponse.of(users.setStaffRoles(account.id(), admin.id(), request.roles()), "Roles saved");
    }
}
