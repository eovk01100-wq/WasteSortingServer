package com.jiwoo.wastesorting_server.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.jiwoo.wastesorting_server.user.Role;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * 액세스 토큰(JWT) 만들기와 검사. 비밀키는 .env의 JWT_SECRET (32자 이상).
 * 액세스 토큰은 짧게(기본 15분) 두고, 길게 쓰는 리프레시 토큰은 DB에 해시로 따로 저장한다.
 */
@Component
public class JwtProvider {

	private final SecretKey key;
	private final long accessMinutes;

	public JwtProvider(@Value("${jwt.secret:}") String secret,
			@Value("${jwt.access-minutes:15}") long accessMinutes) {
		if (secret == null || secret.length() < 32) {
			// 키가 약하면 서버를 아예 켜지 않는다 (실수로 빈 키로 배포되는 것 방지)
			throw new IllegalStateException("JWT_SECRET이 비었거나 32자보다 짧아요. .env를 확인하세요");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
		this.accessMinutes = accessMinutes;
	}

	public String createAccessToken(Long userId, Role role) {
		Instant now = Instant.now();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim("role", role.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(now.plus(accessMinutes, ChronoUnit.MINUTES)))
				.signWith(key)
				.compact();
	}

	public long accessTokenSeconds() {
		return accessMinutes * 60;
	}

	/** 서명·만료를 검사하고 내용(사용자 id, 역할)을 돌려준다. 문제가 있으면 JwtException */
	public AccessClaims parse(String token) throws JwtException {
		Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
		return new AccessClaims(Long.valueOf(c.getSubject()), Role.valueOf(c.get("role", String.class)));
	}

	public record AccessClaims(Long userId, Role role) {
	}
}
