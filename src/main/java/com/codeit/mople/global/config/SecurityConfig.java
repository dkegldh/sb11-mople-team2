package com.codeit.mople.global.config;

import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.security.handler.JsonAccessDeniedHandler;
import com.codeit.mople.domain.auth.security.JsonAuthenticationEntryPoint;
import com.codeit.mople.domain.auth.security.JwtAuthenticationFilter;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.domain.user.repository.UserRepository;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider,
      UserRepository userRepository, ObjectMapper objectMapper,
      CustomOAuth2UserService customOAuth2UserService,
      OAuth2SuccessHandler oAuth2SuccessHandler,
      OAuth2FailureHandler oAuth2FailureHandler) throws Exception {
    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
    csrfTokenRequestHandler.setCsrfRequestAttributeName(null);

    http
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // 쿠키명 기본값 XSRF-TOKEN, 헤더명 X-XSRF-TOKEN
            .csrfTokenRequestHandler(csrfTokenRequestHandler)
            .ignoringRequestMatchers("/api/auth/**", "/api/users") // (POST, 회원가입)는 "아직 로그인하기 전" 상태에서 호출되는 API라서, CSRF 검증에서 예외 처리
        )
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
            .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
            .requestMatchers("/", "/index.html", "/favicon.svg", "/assets/**").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/auth/sign-out").authenticated()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info").permitAll()
            .requestMatchers("/actuator/metrics/**").hasRole("ADMIN")
            .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/users/*/role").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PATCH, "/api/users/*/locked").hasRole("ADMIN")
            .anyRequest().authenticated()
        )
        .oauth2Login(oauth2 -> oauth2
            .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
            .successHandler(oAuth2SuccessHandler)
            .failureHandler(oAuth2FailureHandler))
        .addFilterBefore(
            new JwtAuthenticationFilter(jwtProvider, userRepository),
            UsernamePasswordAuthenticationFilter.class
        );
    return http.build();
  }
}
