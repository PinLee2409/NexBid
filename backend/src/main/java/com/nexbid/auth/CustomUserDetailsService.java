package com.nexbid.auth;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.nexbid.user.UserService;

/**
 * EN: How Spring Security loads an account. Email is the username, matched without regard to case.
 * VI: Cách Spring Security nạp tài khoản. Email đóng vai username, so khớp không phân biệt hoa thường.
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserService users;

    public CustomUserDetailsService(UserService users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return users.findCredentialsByEmail(email)
                .map(NexbidUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
    }
}
