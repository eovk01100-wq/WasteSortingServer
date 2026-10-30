package com.jiwoo.wastesorting_server.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderNotFoundException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.jiwoo.wastesorting_server.common.JsonErrors;
import com.jiwoo.wastesorting_server.user.UserRepository;

/**
 * 주소별 접근 규칙.
 * - 토큰 없이: 상태 확인, 기기 토큰 발급, 로그인, 토큰 재발급, 로그아웃
 * - ADMIN만: /api/v1/admin/** (3주차 검수)
 * - 나머지 /api/v1/** 는 기기 토큰 또는 JWT 필요
 * 세션·쿠키를 쓰지 않으므로(STATELESS) CSRF 보호는 끈다.
 */
@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, JwtProvider jwt)
			throws Exception {
		http
				.csrf(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(a -> a
						.requestMatchers("/api/v1/health", "/api/v1/auth/device", "/api/v1/auth/login",
								"/api/v1/auth/refresh", "/api/v1/auth/logout", "/error")
						.permitAll()
						.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
						.requestMatchers("/api/v1/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(e -> e
						.authenticationEntryPoint((request, response, ex) -> {
							Object reason = request.getAttribute(TokenAuthFilter.AUTH_ERROR);
							if ("TOKEN_EXPIRED".equals(reason)) {
								JsonErrors.write(response, 401, "TOKEN_EXPIRED", "로그인이 만료됐어요. 토큰을 새로 받아 주세요");
							} else if (reason != null) {
								JsonErrors.write(response, 401, "UNAUTHORIZED", "토큰이 올바르지 않아요");
							} else {
								JsonErrors.write(response, 401, "UNAUTHORIZED", "토큰이 필요해요");
							}
						})
						.accessDeniedHandler((request, response, ex) ->
								JsonErrors.write(response, 403, "FORBIDDEN", "권한이 없어요")))
				.addFilterBefore(new TokenAuthFilter(users, jwt), UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Spring 기본 로그인(아이디·비밀번호 폼, Basic)은 쓰지 않는다.
	 * 이 빈이 없으면 Spring Boot가 메모리 사용자 "user"를 만들고
	 * 로그에 "Using generated security password"를 찍는다. 인증은 TokenAuthFilter가 직접 한다.
	 */
	@Bean
	AuthenticationManager authenticationManager() {
		return authentication -> {
			throw new ProviderNotFoundException("기본 로그인은 사용하지 않아요");
		};
	}

	/** 비밀번호는 BCrypt 해시로만 저장 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
