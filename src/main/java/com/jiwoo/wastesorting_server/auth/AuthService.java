package com.jiwoo.wastesorting_server.auth;

import java.time.LocalDateTime;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiwoo.wastesorting_server.analysis.AnalysisRepository;
import com.jiwoo.wastesorting_server.auth.AuthDtos.TokenResponse;
import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.user.User;
import com.jiwoo.wastesorting_server.user.UserRepository;

/**
 * 선택 로그인.
 * - 가입: 지금 쓰는 익명 사용자(기기 토큰)에 이메일·비밀번호를 붙인다 → 그동안의 기록이 그대로 계정 것이 된다
 * - 로그인: 다른 기기에서 계정으로 들어올 때. 그 기기의 익명 기록은 계정으로 옮긴다
 * - 재발급: 리프레시 토큰은 한 번 쓰면 지우고 새로 준다(회전)
 */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	private static final String LOGIN_FAILED = "이메일 또는 비밀번호가 맞지 않아요";

	private final UserRepository users;
	private final RefreshTokenRepository refreshTokens;
	private final AnalysisRepository analyses;
	private final PasswordEncoder encoder;
	private final JwtProvider jwt;
	private final long refreshDays;
	/** 없는 이메일로 로그인할 때도 비슷한 시간이 걸리게 하려고 쓰는 가짜 해시 (계정 존재 여부를 숨김) */
	private final String dummyHash;

	public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, AnalysisRepository analyses,
			PasswordEncoder encoder, JwtProvider jwt, @Value("${jwt.refresh-days:14}") long refreshDays) {
		this.users = users;
		this.refreshTokens = refreshTokens;
		this.analyses = analyses;
		this.encoder = encoder;
		this.jwt = jwt;
		this.refreshDays = refreshDays;
		this.dummyHash = encoder.encode("dummy-password-for-timing");
	}

	@Transactional
	public TokenResponse signup(Long currentUserId, String rawEmail, String password) {
		String email = normalizeEmail(rawEmail);
		User user = users.findById(currentUserId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "토큰이 올바르지 않아요"));
		if (user.isRegistered()) {
			throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "이미 가입된 계정이에요");
		}
		if (users.existsByEmail(email)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 사용 중인 이메일이에요");
		}
		user.registerAccount(email, encoder.encode(password));
		log.info("가입: 사용자 #{}", user.getId()); // 이메일은 로그에 남기지 않는다
		return issue(user);
	}

	@Transactional
	public TokenResponse login(String rawEmail, String password, Long currentUserId) {
		String email = normalizeEmail(rawEmail);
		User user = users.findByEmail(email).orElse(null);
		String hash = user == null ? dummyHash : user.getPasswordHash();
		boolean ok = encoder.matches(password, hash);
		if (user == null || !ok) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", LOGIN_FAILED);
		}
		// 이 기기의 익명 기록이 있으면 계정으로 옮긴다
		if (currentUserId != null && !currentUserId.equals(user.getId())) {
			users.findById(currentUserId).filter(u -> !u.isRegistered()).ifPresent(anon -> {
				int moved = analyses.moveAll(anon.getId(), user.getId());
				log.info("로그인: 익명 #{} 기록 {}건 → 계정 #{}", anon.getId(), moved, user.getId());
			});
		}
		return issue(user);
	}

	@Transactional
	public TokenResponse refresh(String rawRefreshToken) {
		RefreshToken saved = refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken))
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "다시 로그인해 주세요"));
		refreshTokens.delete(saved); // 회전: 한 번 쓴 리프레시 토큰은 바로 무효
		if (saved.isExpired()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "다시 로그인해 주세요");
		}
		User user = users.findById(saved.getUserId())
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "다시 로그인해 주세요"));
		return issue(user);
	}

	@Transactional
	public void logout(String rawRefreshToken) {
		refreshTokens.findByTokenHash(TokenHasher.sha256(rawRefreshToken)).ifPresent(refreshTokens::delete);
	}

	private TokenResponse issue(User user) {
		String refresh = TokenHasher.newRefreshToken();
		refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.sha256(refresh),
				LocalDateTime.now().plusDays(refreshDays)));
		return new TokenResponse(jwt.createAccessToken(user.getId(), user.getRole()), refresh, "Bearer",
				jwt.accessTokenSeconds(), user.getEmail());
	}

	private static String normalizeEmail(String email) {
		return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
	}
}
