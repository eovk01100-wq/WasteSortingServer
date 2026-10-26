package com.jiwoo.wastesorting_server.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 익명 기기 토큰 만들기와 해시.
 * 토큰은 무작위 32바이트라 추측이 불가능하므로 SHA-256 한 번이면 충분하다
 * (비밀번호처럼 사람이 정한 값이 아니라서 BCrypt가 필요 없음).
 */
public final class TokenHasher {

	private static final SecureRandom RANDOM = new SecureRandom();

	private TokenHasher() {
	}

	/** 앱에 한 번만 건네는 토큰 원문. 예: dev_3q2X... */
	public static String newDeviceToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return "dev_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	/** DB에 저장·비교하는 값 (64자 16진수) */
	public static String sha256(String token) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(md.digest(token.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
