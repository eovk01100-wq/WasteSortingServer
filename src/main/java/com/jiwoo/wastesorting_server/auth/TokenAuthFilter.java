package com.jiwoo.wastesorting_server.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jiwoo.wastesorting_server.user.User;
import com.jiwoo.wastesorting_server.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * /api/v1 아래 요청에 "Authorization: Bearer <토큰>"이 있는지 검사한다.
 * 토큰 없이 쓸 수 있는 주소: /api/v1/health, /api/v1/auth/**, /actuator/**
 * 통과하면 요청에 사용자 id를 붙여 둔다 (request attribute "userId").
 */
@Component
@Order(1)
public class TokenAuthFilter extends OncePerRequestFilter {

	public static final String USER_ID = "userId";

	private final UserRepository users;

	public TokenAuthFilter(UserRepository users) {
		this.users = users;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !path.startsWith("/api/v1/")
				|| path.equals("/api/v1/health")
				|| path.startsWith("/api/v1/auth/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			reject(response, "토큰이 필요해요");
			return;
		}
		String token = header.substring(7).trim();
		Optional<User> user = token.isEmpty()
				? Optional.empty()
				: users.findByDeviceTokenHash(TokenHasher.sha256(token));
		if (user.isEmpty()) {
			reject(response, "토큰이 올바르지 않아요");
			return;
		}
		request.setAttribute(USER_ID, user.get().getId());
		chain.doFilter(request, response);
	}

	private static void reject(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
	}
}
