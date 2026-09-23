package com.nexbid.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Baseline security.
 *
 * <p>This is deliberately the smallest thing that lets the health probe answer:
 * without it Spring Security's auto-configuration would put HTTP Basic in front
 * of every path, and a probe that needs a password is not a probe. JWT, roles
 * and the real rule set arrive with functions 05 and 06; until then everything
 * that is not a probe simply stays closed.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // The API is token-based and has no browser session to protect,
                // so there is no CSRF surface to defend.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/health", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                // No form login and no Basic prompt: an unauthenticated call
                // should fail as JSON, not redirect to a login page that this
                // application does not serve.
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}
