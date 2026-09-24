package com.nexbid.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.nexbid.auth.dto.LoginRequest;
import com.nexbid.auth.dto.LoginResponse;
import com.nexbid.auth.dto.RegisterRequest;
import com.nexbid.auth.dto.RegisterResponse;
import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.auth.jwt.JwtService;
import com.nexbid.common.exception.BusinessException;
import com.nexbid.common.exception.ErrorCode;
import java.time.Instant;

import com.nexbid.user.RoleName;
import com.nexbid.user.UserAccount;
import com.nexbid.user.UserCredentials;
import com.nexbid.user.UserService;

/**
 * EN: Registration flow (guide §6): reject a taken email, hash the password, create a BUYER.
 * VI: Luồng đăng ký (guide §6): chặn email đã dùng, hash mật khẩu, tạo tài khoản BUYER.
 */
@Service
public class AuthService {

    private final UserService users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserService users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthenticationManager authenticationManager) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
    }

    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim();

        // EN: The early check gives a clean error; it does not make the operation safe on its own.
        // VI: Kiểm sớm để báo lỗi rõ ràng; bản thân nó chưa đủ an toàn.
        if (users.emailTaken(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }

        try {
            UserAccount account = users.create(
                    request.fullName().trim(),
                    email,
                    passwordEncoder.encode(request.password()),
                    // EN: Everyone starts as a buyer; selling is granted later (guide §6).
                    // VI: Ai cũng bắt đầu là người mua; quyền bán cấp sau (guide §6).
                    RoleName.BUYER);

            return RegisterResponse.from(account);

        } catch (DataIntegrityViolationException ex) {
            // EN: Two requests with the same email can both pass the check above; the unique index settles it.
            // VI: Hai request cùng email đều có thể qua được bước kiểm trên; unique index mới là chốt chặn thật.
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email is already registered");
        }
    }

    /**
     * EN: Login (guide §7). Spring runs the checks; this method only turns their outcome into our error codes.
     * VI: Đăng nhập (guide §7). Spring chạy các bước kiểm tra; hàm này chỉ dịch kết quả sang mã lỗi của mình.
     */
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim();

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));

            UserCredentials user = ((NexbidUserDetails) authentication.getPrincipal()).credentials();
            String token = jwtService.issue(user);

            return LoginResponse.of(token, Instant.now().plus(jwtProperties.expiry()), user);

        } catch (LockedException ex) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "This account has been blocked");

        } catch (AuthenticationException ex) {
            // EN: Unknown email and wrong password answer identically on purpose — otherwise this endpoint
            //     becomes a way to discover which addresses have accounts.
            // VI: Email không tồn tại và sai mật khẩu trả lời giống hệt nhau là có chủ ý — nếu không, endpoint
            //     này thành công cụ dò xem địa chỉ nào đã có tài khoản.
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
    }
}
