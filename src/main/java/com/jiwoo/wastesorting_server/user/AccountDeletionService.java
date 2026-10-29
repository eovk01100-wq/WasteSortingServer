package com.jiwoo.wastesorting_server.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiwoo.wastesorting_server.analysis.AnalysisRepository;
import com.jiwoo.wastesorting_server.auth.RefreshTokenRepository;
import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.misdetection.FrameStorage;
import com.jiwoo.wastesorting_server.misdetection.Misdetection;
import com.jiwoo.wastesorting_server.misdetection.MisdetectionRepository;

/**
 * 탈퇴: 계정(또는 익명 사용자)과 그 사람의 분석 기록, 오탐 사진, 리프레시 토큰을 모두 지운다.
 * 이메일 계정은 실수·도용을 막기 위해 비밀번호를 한 번 더 확인한다.
 * 글자 검색 캐시(guide)는 누가 검색했는지 담지 않으므로 남긴다.
 */
@Service
public class AccountDeletionService {

	private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

	private final UserRepository users;
	private final AnalysisRepository analyses;
	private final MisdetectionRepository misdetections;
	private final RefreshTokenRepository refreshTokens;
	private final FrameStorage storage;
	private final PasswordEncoder encoder;

	public AccountDeletionService(UserRepository users, AnalysisRepository analyses,
			MisdetectionRepository misdetections, RefreshTokenRepository refreshTokens, FrameStorage storage,
			PasswordEncoder encoder) {
		this.users = users;
		this.analyses = analyses;
		this.misdetections = misdetections;
		this.refreshTokens = refreshTokens;
		this.storage = storage;
		this.encoder = encoder;
	}

	@Transactional
	public void delete(Long userId, String password) {
		User user = users.findById(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "토큰이 올바르지 않아요"));
		if (user.isRegistered() && (password == null || !encoder.matches(password, user.getPasswordHash()))) {
			throw new ApiException(HttpStatus.FORBIDDEN, "WRONG_PASSWORD", "비밀번호가 맞지 않아요");
		}

		int photos = 0;
		for (Misdetection m : misdetections.findByUserId(userId)) {
			storage.delete(m.getFrameFile());
			misdetections.delete(m);
			photos++;
		}
		int records = analyses.deleteAllByUser(userId);
		refreshTokens.deleteAllByUser(userId);
		users.delete(user);
		log.info("탈퇴: 사용자 #{} (기록 {}건, 오탐 사진 {}장 삭제)", userId, records, photos);
	}
}
