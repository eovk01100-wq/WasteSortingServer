package com.jiwoo.wastesorting_server.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인 관련 요청·응답 모양 */
public final class AuthDtos {

	private AuthDtos() {
	}

	public record SignupRequest(
			@NotBlank(message = "이메일을 입력해 주세요") @Email(message = "이메일 형식이 아니에요") @Size(max = 100, message = "이메일이 너무 길어요") String email,
			@NotBlank(message = "비밀번호를 입력해 주세요") @Size(min = 8, max = 64, message = "비밀번호는 8~64자로 정해 주세요") String password) {
	}

	public record LoginRequest(
			@NotBlank(message = "이메일을 입력해 주세요") String email,
			@NotBlank(message = "비밀번호를 입력해 주세요") String password) {
	}

	public record RefreshRequest(@NotBlank(message = "refreshToken이 필요해요") String refreshToken) {
	}

	public record TokenResponse(String accessToken, String refreshToken, String tokenType, long expiresIn,
			String email) {
	}
}
