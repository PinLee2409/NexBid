package com.nexbid.user;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * EN: Grants ADMIN and SELLER at startup to accounts named in configuration. The first admin cannot come
 *     from the public register endpoint, and hard-coding one in a migration would ship a known password.
 * VI: Cấp ADMIN và SELLER lúc khởi động cho các tài khoản khai trong cấu hình. Admin đầu tiên không thể
 *     tạo qua endpoint đăng ký công khai, và nhét cứng vào migration thì vô tình phát tán một mật khẩu ai cũng biết.
 */
@Configuration
public class UserBootstrap {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    /**
     * EN: Empty by default, so nothing is granted unless someone asks for it.
     * VI: Mặc định để trống, nên không có gì được cấp trừ khi ai đó yêu cầu.
     */
    @ConfigurationProperties(prefix = "nexbid.bootstrap")
    public record BootstrapProperties(List<String> adminEmails, List<String> sellerEmails) {

        public BootstrapProperties {
            adminEmails = adminEmails == null ? List.of() : adminEmails;
            sellerEmails = sellerEmails == null ? List.of() : sellerEmails;
        }
    }

    @Bean
    ApplicationRunner grantConfiguredRoles(UserService users, BootstrapProperties properties) {
        return args -> {
            grant(users, properties.adminEmails(), RoleName.ADMIN);
            grant(users, properties.sellerEmails(), RoleName.SELLER);
        };
    }

    private static void grant(UserService users, List<String> emails, RoleName role) {
        for (String email : emails) {
            try {
                users.grantRole(email, role);
                log.info("Granted {} to {}", role, email);
            } catch (RuntimeException ex) {
                // EN: A configured account that has not registered yet must not stop the application.
                // VI: Tài khoản khai trong cấu hình nhưng chưa đăng ký thì không được làm chết ứng dụng.
                log.warn("Could not grant {} to {}: {}", role, email, ex.getMessage());
            }
        }
    }
}
