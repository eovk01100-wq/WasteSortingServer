package com.jiwoo.wastesorting_server.auth;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jiwoo.wastesorting_server.user.Role;
import com.jiwoo.wastesorting_server.user.UserRepository;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * "Authorization: Bearer <토큰>"을 읽어 누가 보낸 요청인지 정한다 (Spring Security 필터 체인 안에서 실행).
 * - dev_ 로 시작: 익명 기기 토큰 → DB에서 해시로 찾는다
 * - 그 밖: 로그인 후 받은 JWT 액세스 토큰 → 서명·만료만 검사 (DB 조회 없음)
 * 토큰이 없거나 틀려도 여기서는 막지 않는다. 막을지는 SecurityConfig의 주소별 규칙이 정하고,
 * 막을 때 보여 줄 이유를 request attribute(AUTH_ERROR)로 남겨 둔다.
 * SecurityConfig에서 직접 만들어 넣으므로 @Component를 붙이지 않는다 (서블릿 필터로 두 번 등록되는 것 방지).
 */
public class TokenAuthFilter extends OncePerRequestFilter {

	/** 컨트롤러와 사용량 제한 필터가 읽는 사용자 id */
	public static final String USER_ID = "userId";
	public static final String AUTH_ERROR = "authError";
	public static final String DEVICE_PREFIX = "dev_";

	private final UserRepository users;
	private final JwtProvider jwt;

	public TokenAuthFilter(UserRepository users, JwtProvider jwt) {
		this.users = users;
		this.jwt = jwt;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String header = request.getHeader("Authorization");
		if (header != null && header.startsWith("Bearer ")) {
			String token = header.substring(7).trim();
			if (token.startsWith(DEVICE_PREFIX)) {
				users.findByDeviceTokenHash(TokenHasher.sha256(token)).ifPresentOrElse(
						u -> authenticate(request, u.getId(), u.getRole()),
						() -> request.setAttribute(AUTH_ERROR, "INVALID_TOKEN"));
			} else if (!token.isEmpty()) {
				try {
					JwtProvider.AccessClaims c = jwt.parse(token);
					if (users.existsById(c.userId())) {
						authenticate(request, c.userId(), c.role());
					} else {
						request.setAttribute(AUTH_ERROR, "INVALID_TOKEN"); // 탈퇴한 사용자의 남은 토큰
					}
				} catch (ExpiredJwtException e) {
					request.setAttribute(AUTH_ERROR, "TOKEN_EXPIRED");
				} catch (JwtException | IllegalArgumentException e) {
					request.setAttribute(AUTH_ERROR, "INVALID_TOKEN");
				}
			}
		}
		chain.doFilter(request, response);
	}

	private static void authenticate(HttpServletRequest request, Long userId, Role role) {
		request.setAttribute(USER_ID, userId);
		var auth = new UsernamePasswordAuthenticationToken(userId, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}
}
