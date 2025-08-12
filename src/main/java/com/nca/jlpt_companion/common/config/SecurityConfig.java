package com.nca.jlpt_companion.common.config;

import com.nca.jlpt_companion.auth.security.JwtAuthFilter;
import com.nca.jlpt_companion.auth.service.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenService jwtTokenService;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable);
        http.addFilterBefore(new JwtAuthFilter(jwtTokenService), UsernamePasswordAuthenticationFilter.class);

        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/swagger-ui.html","/swagger-ui/**","/v3/api-docs/**",
                        "/actuator/**",
                        "/api/v1/auth/**",
                        "/api/v1/content/**"
                ).permitAll()
                .requestMatchers("/api/v1/sync/**").authenticated()
                .anyRequest().permitAll()
        );

        return http.build();
    }
}
