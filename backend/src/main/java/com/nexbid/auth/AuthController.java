package com.nexbid.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nexbid.auth.dto.LoginRequest;
import com.nexbid.auth.dto.LoginResponse;
import com.nexbid.auth.dto.RegisterRequest;
import com.nexbid.auth.dto.RegisterResponse;
import com.nexbid.common.response.ApiResponse;

import jakarta.validation.Valid;

/**
 * EN: Public authentication endpoints. Everything here is reachable without a token, by definition.
 * VI: Các endpoint xác thực công khai. Theo đúng bản chất, chỗ này gọi được khi chưa có token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.of(authService.register(request), "Account created");
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.of(authService.login(request), "Signed in");
    }
}
