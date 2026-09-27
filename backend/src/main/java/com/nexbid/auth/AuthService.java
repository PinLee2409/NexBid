package com.nexbid.auth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexbid.auth.dto.LoginRequest;
import com.nexbid.auth.dto.LoginResponse;
import com.nexbid.auth.dto.RegisterRequest;
import com.nexbid.auth.dto.RegisterResponse;
import com.nexbid.auth.jwt.JwtProperties;
import com.nexbid.auth.jwt.JwtService;
import com.nexbid.auth.refresh.RefreshTokenService;
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
    private final ApplicationEventPublisher events;
    private final RefreshTokenService refreshTokens;
    private final LoginAttempts attempts;

    /**
     * EN: What a sign-in or a renewal hands back: the access token in the body, the refresh token for the cookie.
     * VI: Thứ mà đăng nhập hoặc gia hạn trả về: access token trong body, refresh token để đặt vào cookie.
     */
    public record Session(LoginResponse response, RefreshTokenService.Issued refresh) {
    }

    public AuthService(
            UserService users,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties,
            AuthenticationManager authenticationManager,
            ApplicationEventPublisher events,
            RefreshTokenService refreshTokens,
            LoginAttempts attempts) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
        this.events = events;
        this.refreshTokens = refreshTokens;
        this.attempts = attempts;
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
    @Transactional
    public Session login(LoginRequest request, String clientAddress) {
        String email = request.email().trim();
        // EN: Checked first: someone over the limit learns nothing, not even whether the password was right.
        // VI: Kiểm tra trước tiên: ai đã vượt giới hạn thì không biết được gì, kể cả mật khẩu có đúng hay không.
        attempts.checkAllowed(email, clientAddress);

        try {
            var authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.password()));

            UserCredentials user = ((NexbidUserDetails) authentication.getPrincipal()).credentials();
            attempts.clear(email, clientAddress);
            events.publishEvent(new SuccessfulLoginEvent(user.id()));

            return new Session(accessFor(user), refreshTokens.start(user.id(), request.remember()));

        } catch (LockedException ex) {
            throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED, "This account has been blocked");

        } catch (AuthenticationException ex) {
            attempts.recordFailure(email, clientAddress);
            // EN: Unknown email and wrong password answer identically on purpose — otherwise this endpoint
            //     becomes a way to discover which addresses have accounts.
            // VI: Email không tồn tại và sai mật khẩu trả lời giống hệt nhau là có chủ ý — nếu không, endpoint
            //     này thành công cụ dò xem địa chỉ nào đã có tài khoản.
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
        }
    }

    /**
     * EN: Renews a session from its refresh token (spec §7.2). Deliberately outside a transaction: the family of a
     *     replayed token is revoked and committed before this refuses the request.
     * VI: Gia hạn phiên từ refresh token của nó (spec §7.2). Cố ý nằm ngoài transaction: họ của token bị dùng lại
     *     được thu hồi và commit trước khi hàm này từ chối request.
     */
    public Session refresh(String presentedToken) {
        RefreshTokenService.Renewal renewal = refreshTokens.renew(presentedToken)
                .orElseThrow(AuthService::signInAgain);
        UserCredentials user = users.findCredentialsById(renewal.userId()).orElseThrow(AuthService::signInAgain);
        return new Session(accessFor(user), renewal.next());
    }

    /** EN: Signs out this device: its refresh token stops working. / VI: Đăng xuất thiết bị này: refresh token của nó hết dùng được. */
    public void logout(String presentedToken) {
        refreshTokens.end(presentedToken);
    }

    private LoginResponse accessFor(UserCredentials user) {
        return LoginResponse.of(jwtService.issue(user), Instant.now().plus(jwtProperties.expiry()), user);
    }

    private static BusinessException signInAgain() {
        return new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID, "The session has ended; sign in again");
    }
}
