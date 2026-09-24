package com.nexbid.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.nexbid.user.UserCredentials;
import com.nexbid.user.UserStatus;

/**
 * EN: Adapts a NexBid account to what Spring Security expects, so the framework can run the checks.
 * VI: Chuyển tài khoản NexBid sang dạng Spring Security hiểu, để framework tự chạy các bước kiểm tra.
 */
public class NexbidUserDetails implements UserDetails {

    private final UserCredentials user;

    public NexbidUserDetails(UserCredentials user) {
        this.user = user;
    }

    public UserCredentials credentials() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // EN: ROLE_ prefix, otherwise hasRole("SELLER") will never match.
        // VI: Cần tiền tố ROLE_, nếu không hasRole("SELLER") sẽ không bao giờ khớp.
        return user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    @Override
    public String getPassword() {
        return user.passwordHash();
    }

    @Override
    public String getUsername() {
        return user.email();
    }

    /**
     * EN: A blocked account keeps its history but cannot sign in — this is what makes UserStatus mean something.
     * VI: Tài khoản bị khoá vẫn giữ lịch sử nhưng không đăng nhập được — đây là chỗ UserStatus thực sự có tác dụng.
     */
    @Override
    public boolean isAccountNonLocked() {
        return user.status() != UserStatus.BLOCKED;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    static List<String> roleNames(UserDetails details) {
        return details.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }
}
