package com.nexbid.infrastructure.config;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.nexbid.auth.JwtAuthenticationFilter;
import com.nexbid.common.exception.ErrorCode;
import com.nexbid.common.response.ErrorResponse;
import com.nexbid.user.RoleName;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * EN: Baseline security — the two probes stay open, everything else is closed until JWT arrives (function 05).
 * VI: Bảo mật nền — mở hai probe, đóng toàn bộ phần còn lại cho tới khi có JWT (chức năng 05).
 */
@Configuration
@EnableWebSecurity
// EN: Turns on @PreAuthorize, for rules a URL pattern cannot express — "only the seller who owns this lot".
// VI: Bật @PreAuthorize, cho những luật mà mẫu URL không diễn tả được — "chỉ người bán sở hữu lô này".
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * EN: Paths that must answer without credentials. Kept short on purpose.
     * VI: Các đường phải trả lời khi chưa đăng nhập. Cố ý giữ thật ngắn.
     */
    // EN: Registration and login must be reachable without a token — that is what they are for.
    // VI: Đăng ký và đăng nhập phải gọi được khi chưa có token — vốn dĩ chúng sinh ra để làm thế.
    static final String[] PUBLIC_PATHS = {
            "/api/health", "/actuator/health", "/api/auth/**",
            // EN: Every countdown on the site measures against this, so it must answer before sign-in.
            // VI: Mọi đồng hồ đếm ngược trên site đo theo mốc này, nên nó phải trả lời từ trước khi đăng nhập.
            "/api/server-time",
            // EN: Browsing is public — someone deciding whether to join must see what is on offer.
            // VI: Duyệt hàng là công khai — người đang cân nhắc tham gia phải xem được có gì.
            "/api/categories", "/api/categories/**",
            "/api/auctions", "/api/auctions/**",
            // EN: A browser loading <img> sends no Authorization header, so product photos must be open.
            // VI: Trình duyệt nạp thẻ <img> không gửi header Authorization, nên ảnh sản phẩm phải mở.
            "/media/**",
            // EN: The realtime channel carries only what the public page already shows.
            // VI: Kênh realtime chỉ chở những thứ trang công khai vốn đã hiển thị.
            "/ws", "/ws/**"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http
                // EN: Token-based API with no browser session, so there is no CSRF surface.
                // VI: API dùng token, không có session trình duyệt, nên không có bề mặt CSRF.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // EN: Carved out before the public rule below: reading a lot is open to anyone,
                        //     offering money for it, watching it or setting an auto bid on it is not.
                        // VI: Khoét ra trước luật công khai bên dưới: xem một lô thì ai cũng được, còn bỏ
                        //     tiền ra mua, theo dõi hay đặt auto bid thì không.
                        .requestMatchers(HttpMethod.POST, "/api/auctions/*/bids").authenticated()
                        .requestMatchers("/api/auctions/*/watch").authenticated()
                        .requestMatchers("/api/auctions/*/auto-bid").authenticated()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // EN: The frontend also hides these menus, but that is decoration —
                        //     this is the line that actually stops someone typing the URL.
                        // VI: Frontend cũng ẩn các menu này, nhưng đó chỉ là trang trí —
                        //     đây mới là chỗ thật sự chặn người gõ thẳng URL.
                        .requestMatchers("/api/admin/**").hasRole(RoleName.ADMIN.name())
                        .requestMatchers("/api/seller/**").hasRole(RoleName.SELLER.name())
                        .anyRequest().authenticated())
                // EN: No form login, no Basic prompt — failures must be JSON, not a login page.
                // VI: Không form login, không Basic — lỗi phải trả JSON, không phải trang đăng nhập.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                // EN: Without these two, the filter chain replies with an empty body.
                // VI: Thiếu hai cái này, filter chain sẽ trả về body rỗng.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint(objectMapper))
                        .accessDeniedHandler(accessDeniedHandler(objectMapper)))
                // EN: Before the username/password filter, so a valid token authenticates the request
                //     before anything else tries to.
                // VI: Đặt trước filter username/password, để token hợp lệ xác thực request trước khi
                //     có thứ khác kịp xen vào.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * EN: Exposes the manager so AuthService can ask Spring to run the credential checks.
     * VI: Cung cấp manager để AuthService nhờ Spring chạy các bước kiểm tra thông tin đăng nhập.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration)
            throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * EN: BCrypt — deliberately slow, so a stolen password table is expensive to crack.
     * VI: BCrypt — cố ý chạy chậm, để bảng mật khẩu nếu bị lấy cắp cũng rất tốn công bẻ.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * EN: No credentials at all — tell the client to sign in.
     * VI: Chưa có thông tin đăng nhập — báo client đăng nhập.
     */
    private AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) ->
                write(objectMapper, response, ErrorCode.NOT_AUTHENTICATED, "Authentication is required");
    }

    /**
     * EN: Signed in but not allowed — signing in again will not help.
     * VI: Đã đăng nhập nhưng không đủ quyền — đăng nhập lại cũng vô ích.
     */
    private AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, deniedException) ->
                write(objectMapper, response, ErrorCode.ACCESS_DENIED, "You may not perform this action");
    }

    private static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            ErrorCode code,
            String message) throws IOException {

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
    }
}
