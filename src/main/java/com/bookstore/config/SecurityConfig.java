package com.bookstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String ROLE = "role";
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String ROLE_ = "ROLE_";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // JWT is stateless (no sessions stored)
    // csrf protection is disabled because JWT is in headers, not in cookies
    // order of permissions is important
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/books/**")
                        .permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/categories/**")
                        .permitAll()

                        .requestMatchers("/api/books/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/categories/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                );

        return http.build();
    }

    // converts the JWT role claim into a Spring Security authority ( "role": "ADMIN" -> ROLE_USER)
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String role = jwt.getClaimAsString(ROLE);

            if (!ADMIN.equals(role) && !USER.equals(role)) {
                return List.of();
            }

            return List.of(new SimpleGrantedAuthority(ROLE_ + role));
        });

        return converter;
    }
}
