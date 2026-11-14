package com.splitsmart.security;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    static PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(
                                                "/",
                                                "/groups/{id}",
                                                "/index.html",
                                                "/app.js",
                                                "/style.css",
                                                "/error",
                                                "/favicon.ico",
                                                "/api/auth/csrf",
                                                "/api/auth/login",
                                                "/api/auth/register")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .requestCache(cache -> cache.disable())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(
                        login ->
                                login.loginProcessingUrl("/api/auth/login")
                                        .successHandler(
                                                (request, response, authentication) -> {
                                                    response.setContentType("application/json");
                                                    response.setHeader("Cache-Control", "no-store");
                                                    response.getWriter().write("{\"ok\":true}");
                                                })
                                        .failureHandler(
                                                (request, response, exception) -> {
                                                    response.setStatus(401);
                                                    response.setContentType("application/json");
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"error\":\"Invalid username or password.\"}");
                                                }))
                .logout(
                        logout ->
                                logout.logoutUrl("/api/auth/logout")
                                        .deleteCookies("JSESSIONID")
                                        .logoutSuccessHandler(
                                                (request, response, authentication) -> {
                                                    response.setContentType("application/json");
                                                    response.getWriter().write("{\"ok\":true}");
                                                }))
                .sessionManagement(
                        session -> session.sessionFixation(fixation -> fixation.changeSessionId()))
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, e) -> {
                                                    response.setStatus(401);
                                                    response.setContentType("application/json");
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"error\":\"Please sign in.\"}");
                                                })
                                        .accessDeniedHandler(
                                                (request, response, e) -> {
                                                    response.setStatus(403);
                                                    response.setContentType("application/json");
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"error\":\"Access denied or security token expired. Refresh and try again.\"}");
                                                }));
        return http.build();
    }
}
