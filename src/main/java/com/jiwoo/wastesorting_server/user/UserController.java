package com.jiwoo.wastesorting_server.user;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jiwoo.wastesorting_server.auth.TokenAuthFilter;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "계정", description = "가입, 로그인, 토큰 재발급, 로그아웃, 탈퇴")
public class UserController {

	private final AccountDeletionService deletion;

	public UserController(AccountDeletionService deletion) {
		this.deletion = deletion;
	}

	/**
	 * 탈퇴: 계정과 기록, 오탐 사진을 모두 지운다 (되돌릴 수 없음).
	 * 이메일 계정은 {"password":"..."}가 필요하고, 익명 사용자는 본문 없이 보내면 된다.
	 */
	@DeleteMapping("/me")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMe(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@RequestBody(required = false) DeleteRequest req) {
		deletion.delete(userId, req == null ? null : req.password());
	}

	public record DeleteRequest(String password) {
	}
}
