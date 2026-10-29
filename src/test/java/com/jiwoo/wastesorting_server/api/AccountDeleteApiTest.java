package com.jiwoo.wastesorting_server.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

/** 탈퇴: 지우면 그 토큰으로 더는 못 들어오고, 이메일 계정은 비밀번호 확인 */
class AccountDeleteApiTest extends ApiTestSupport {

	@Test
	@DisplayName("익명 사용자는 본문 없이 탈퇴할 수 있고, 이후 그 기기 토큰은 401")
	void anonymousDelete() throws Exception {
		when(gemini.generate(contains("탈퇴테스트"), any(), any())).thenReturn(PET_JSON);
		String device = bearer(deviceToken());
		mvc.perform(get("/api/v1/search").param("q", "탈퇴테스트").header("Authorization", device))
				.andExpect(status().isOk());

		mvc.perform(delete("/api/v1/users/me").header("Authorization", device))
				.andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/analyses").header("Authorization", device))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("이메일 계정은 비밀번호가 틀리면 403, 맞으면 탈퇴되고 남은 JWT도 401")
	void registeredDeleteNeedsPassword() throws Exception {
		String email = "d" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		String body = mvc.perform(post("/api/v1/auth/signup").header("Authorization", bearer(deviceToken()))
				.contentType(json()).content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String access = bearer(JsonPath.read(body, "$.accessToken"));

		mvc.perform(delete("/api/v1/users/me").header("Authorization", access)
				.contentType(json()).content("{\"password\":\"wrongpass1\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));

		mvc.perform(delete("/api/v1/users/me").header("Authorization", access)
				.contentType(json()).content("{\"password\":\"password123\"}"))
				.andExpect(status().isNoContent());

		// 15분짜리 JWT가 아직 살아 있어도 탈퇴한 사용자면 거절
		mvc.perform(get("/api/v1/analyses").header("Authorization", access))
				.andExpect(status().isUnauthorized());
		// 같은 이메일로 다시 로그인할 수 없다
		mvc.perform(post("/api/v1/auth/login").contentType(json())
				.content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
				.andExpect(status().isUnauthorized());
	}
}
