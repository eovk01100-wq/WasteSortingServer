package com.jiwoo.wastesorting_server.auth;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.jiwoo.wastesorting_server.user.User;
import com.jiwoo.wastesorting_server.user.UserRepository;

@RestController
@Tag(name = "계정", description = "가입, 로그인, 토큰 재발급, 로그아웃")
@RequestMapping("/api/v1/auth")
public class DeviceAuthController {

	private final UserRepository users;

	public DeviceAuthController(UserRepository users) {
		this.users = users;
	}

	/** 앱 첫 실행 때 한 번 호출 → 익명 사용자를 만들고 토큰을 돌려준다 */
	@PostMapping("/device")
	@ResponseStatus(HttpStatus.CREATED)
	@Transactional
	public DeviceTokenResponse issue() {
		String token = TokenHasher.newDeviceToken();
		users.save(User.anonymous(TokenHasher.sha256(token)));
		return new DeviceTokenResponse(token);
	}
}
