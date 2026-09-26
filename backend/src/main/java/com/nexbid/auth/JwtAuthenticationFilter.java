package com.nexbid.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.nexbid.auth.jwt.JwtService;
import com.nexbid.user.UserService;
import com.nexbid.user.UserStatus;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * EN: Part of this module's public surface: the security chain in infrastructure has to be able to install it.
 * VI: Thuộc phần công khai của module: chuỗi bảo mật bên infrastructure phải gắn được nó vào.
 *
 * <p>EN: Reads `Authorization: Bearer …` on every request and, if the token is valid, marks the caller authenticated.
 * <p>VI: Đọc `Authorization: Bearer …` ở mỗi request, token hợp lệ thì đánh dấu người gọi đã xác thực.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService users;

    public JwtAuthenticationFilter(JwtService jwtService, UserService users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith(PREFIX)) {
            jwtService.read(header.substring(PREFIX.length()))
                    .filter(user -> users.findById(user.id())
                            .map(account -> account.status() == UserStatus.ACTIVE)
                            .orElse(false))
                    .ifPresent(user -> {
                // EN: Spring expects ROLE_ prefixed authorities for hasRole(...) to match.
                // VI: Spring cần authority có tiền tố ROLE_ thì hasRole(...) mới khớp.
                List<SimpleGrantedAuthority> authorities = user.roles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .toList();

                var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
        }

        // EN: A missing or bad token is not an error here — the request simply stays anonymous and
        //     the authorisation rules decide. That keeps public endpoints working.
        // VI: Thiếu token hay token hỏng không phải lỗi ở đây — request cứ để ẩn danh và để luật phân
        //     quyền quyết định. Nhờ vậy các endpoint công khai vẫn chạy bình thường.
        chain.doFilter(request, response);
    }
}
