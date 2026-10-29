package com.jiwoo.wastesorting_server.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.jiwoo.wastesorting_server.auth.AuthDtos.LoginRequest;
import com.jiwoo.wastesorting_server.auth.AuthDtos.RefreshRequest;
import com.jiwoo.wastesorting_server.auth.AuthDtos.SignupRequest;
import com.jiwoo.wastesorting_server.auth.AuthDtos.TokenResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AccountController {

	private final AuthService auth;

	public AccountController(AuthService auth) {
		this.auth = auth;
	}

	/** 가입: 지금 기기 토큰의 익명 사용자를 계정으로 바꾼다 (기기 토큰 필요) */
	@PostMapping("/signup")
	@ResponseStatus(HttpStatus.CREATED)
	public TokenResponse signup(@RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@Valid @RequestBody SignupRequest req) {
		return auth.signup(userId, req.email(), req.password());
	}

	/** 로그인 (토큰 없이 가능. 기기 토큰을 같이 보내면 그 기기의 기록을 계정으로 옮긴다) */
	@PostMapping("/login")
	public TokenResponse login(@RequestAttribute(name = TokenAuthFilter.USER_ID, required = false) Long userId,
			@Valid @RequestBody LoginRequest req) {
		return auth.login(req.email(), req.password(), userId);
	}

	/** 액세스 토큰이 만료되면 리프레시 토큰으로 새로 받는다 (리프레시 토큰도 새로 바뀜) */
	@PostMapping("/refresh")
	public TokenResponse refresh(@Valid @RequestBody RefreshRequest req) {
		return auth.refresh(req.refreshToken());
	}

	/** 로그아웃: 리프레시 토큰을 지운다 */
	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@Valid @RequestBody RefreshRequest req) {
		auth.logout(req.refreshToken());
	}
}
