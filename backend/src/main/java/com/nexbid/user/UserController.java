package com.nexbid.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.exception.ResourceNotFoundException;
import com.nexbid.common.response.ApiResponse;
import com.nexbid.common.security.CurrentUser;
import com.nexbid.user.dto.UpdateProfileRequest;

/**
 * EN: The signed-in user's own profile (guide §9).
 * VI: Hồ sơ của chính người đang đăng nhập (guide §9).
 *
 * <p>EN: Both methods identify the caller from the token. Accepting an id from the request would let anyone
 *     read or rename anyone else's account by changing one number.
 * <p>VI: Cả hai đều lấy danh tính từ token. Nếu nhận id từ request thì ai cũng đọc hoặc đổi tên tài khoản
 *     người khác chỉ bằng cách sửa một con số.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public ApiResponse<UserAccount> me(@AuthenticationPrincipal CurrentUser caller) {
        return ApiResponse.of(load(caller));
    }

    @PutMapping("/me")
    public ApiResponse<UserAccount> updateMe(
            @AuthenticationPrincipal CurrentUser caller,
            @jakarta.validation.Valid @RequestBody UpdateProfileRequest request) {

        return ApiResponse.of(
                users.updateFullName(caller.id(), request.fullName()),
                "Profile updated");
    }

    /**
     * EN: The token was valid but the account is gone — deleted while a token was still in someone's hands.
     * VI: Token hợp lệ nhưng tài khoản không còn — bị xoá trong lúc ai đó vẫn đang giữ token.
     */
    private UserAccount load(CurrentUser caller) {
        return users.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.USER_NOT_FOUND, "Account no longer exists"));
    }
}
