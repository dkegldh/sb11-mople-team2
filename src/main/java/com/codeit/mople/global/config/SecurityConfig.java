package com.codeit.mople.global.config;

import com.codeit.mople.domain.auth.repository.AccountLockRepository;
import com.codeit.mople.domain.auth.repository.SessionTokenRepository;
import com.codeit.mople.domain.auth.security.CustomOAuth2UserService;
import com.codeit.mople.domain.auth.security.handler.JsonAccessDeniedHandler;
import com.codeit.mople.domain.auth.security.JsonAuthenticationEntryPoint;
import com.codeit.mople.domain.auth.security.JwtAuthenticationFilter;
import com.codeit.mople.domain.auth.security.handler.OAuth2FailureHandler;
import com.codeit.mople.domain.auth.security.handler.OAuth2SuccessHandler;
import com.codeit.mople.global.jwt.JwtProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityConfig.CorsProperties.class)
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtProvider jwtProvider,
      AccountLockRepository accountLockRepository, SessionTokenRepository sessionTokenRepository,
      ObjectMapper objectMapper,
      CustomOAuth2UserService customOAuth2UserService,
      OAuth2SuccessHandler oAuth2SuccessHandler,
      OAuth2FailureHandler oAuth2FailureHandler,
      CorsConfigurationSource corsConfigurationSource) throws Exception {
    CsrfTokenRequestAttributeHandler csrfTokenRequestHandler = new CsrfTokenRequestAttributeHandler();
    csrfTokenRequestHandler.setCsrfRequestAttributeName(null);

    http
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()) // 쿠키명 기본값 XSRF-TOKEN, 헤더명 X-XSRF-TOKEN
            .csrfTokenRequestHandler(csrfTokenRequestHandler)
            // 로그인 전(비인증) 상태에서 호출되는 API라서 CSRF 검증에서 예외 처리.
            // /api/auth/sign-out은 제외: 인증 없이도 처리되므로(만료/무효화된 세션도 로그아웃 가능),
            // CSRF 토큰 검증으로 강제 로그아웃(CSRF) 공격을 방어한다.
            .ignoringRequestMatchers(
                "/api/auth/sign-in", "/api/auth/refresh", "/api/auth/reset-password", "/api/users")
        )
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(new JsonAuthenticationEntryPoint(objectMapper))
            .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
            .requestMatchers("/", "/index.html", "/favicon.svg", "/assets/**", "/uploads/**").permitAll()
            .requestMatchers("/ws/**").permitAll()
            .requestMatchers(HttpMethod.POST, "/api/users").permitAll()
            .requestMatchers("/api/auth/**").permitAll()
            .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
            .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
            .requestMatchers("/actuator/**").hasRole("ADMIN")
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
            new JwtAuthenticationFilter(jwtProvider, accountLockRepository, sessionTokenRepository),
            UsernamePasswordAuthenticationFilter.class
        );
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
    configuration.setAllowedMethods(List.of("POST", "GET", "PATCH", "DELETE", "PUT", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setAllowCredentials(true);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }

  @Getter
  @Validated
  @ConfigurationProperties(prefix = "app.cors")
  public static class CorsProperties {
    @NotEmpty
    private final List<String> allowedOrigins = new ArrayList<>(List.of());
  }
}
