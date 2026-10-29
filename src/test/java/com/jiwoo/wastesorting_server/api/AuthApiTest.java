package com.jiwoo.wastesorting_server.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** 가입·로그인·토큰 재발급(회전) */
class AuthApiTest extends ApiTestSupport {

	private static String uniqueEmail() {
		return "u" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

	private static String account(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}

	@Test
	@DisplayName("가입하면 그동안의 익명 기록이 계정으로 이어진다")
	void signupKeepsAnonymousHistory() throws Exception {
		when(gemini.generate(contains("가입전기록"), any(), any())).thenReturn(PET_JSON);
		String device = bearer(deviceToken());
		mvc.perform(get("/api/v1/search").param("q", "가입전기록").header("Authorization", device))
				.andExpect(status().isOk());

		String body = mvc.perform(post("/api/v1/auth/signup").header("Authorization", device)
				.contentType(json()).content(account(uniqueEmail(), "password123")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.refreshToken").isString())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String access = JsonPath.read(body, "$.accessToken");

		mvc.perform(get("/api/v1/analyses").header("Authorization", bearer(access)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].query").value("가입전기록"));
	}

	@Test
	@DisplayName("짧은 비밀번호는 400 VALIDATION")
	void shortPassword() throws Exception {
		mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content(account(uniqueEmail(), "123")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION"));
	}

	@Test
	@DisplayName("같은 이메일로 두 번 가입하면 409 EMAIL_TAKEN")
	void duplicateEmail() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content(account(email, "password123")))
				.andExpect(status().isCreated());
		mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content(account(email, "password456")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
	}

	@Test
	@DisplayName("틀린 비밀번호와 없는 이메일은 같은 401 LOGIN_FAILED")
	void loginFailureLooksTheSame() throws Exception {
		String email = uniqueEmail();
		mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content(account(email, "password123")))
				.andExpect(status().isCreated());

		mvc.perform(post("/api/v1/auth/login").contentType(json()).content(account(email, "wrongpass1")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
		mvc.perform(post("/api/v1/auth/login").contentType(json()).content(account(uniqueEmail(), "password123")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
	}

	@Test
	@DisplayName("리프레시 토큰은 한 번만 쓸 수 있다 (회전)")
	void refreshTokenRotation() throws Exception {
		String body = mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content(account(uniqueEmail(), "password123")))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String refresh = JsonPath.read(body, "$.refreshToken");
		String req = "{\"refreshToken\":\"" + refresh + "\"}";

		mvc.perform(post("/api/v1/auth/refresh").contentType(json()).content(req))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").isString());
		mvc.perform(post("/api/v1/auth/refresh").contentType(json()).content(req))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_REFRESH"));
	}
}
