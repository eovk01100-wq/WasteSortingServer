package com.jiwoo.wastesorting_server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 주소별 접근 규칙: 토큰 없음 401, 틀린 토큰 401, ADMIN 아니면 403, 잘못된 방식 405 */
class SecurityApiTest extends ApiTestSupport {

	@Test
	@DisplayName("상태 확인은 토큰 없이 된다")
	void healthIsPublic() throws Exception {
		mvc.perform(get("/api/v1/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	@DisplayName("토큰 없이 검색하면 401 '토큰이 필요해요'")
	void searchWithoutToken() throws Exception {
		mvc.perform(get("/api/v1/search").param("q", "캔"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value("토큰이 필요해요"));
	}

	@Test
	@DisplayName("없는 기기 토큰은 401 '토큰이 올바르지 않아요'")
	void unknownDeviceToken() throws Exception {
		mvc.perform(get("/api/v1/analyses").header("Authorization", bearer("dev_not-a-real-token")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("토큰이 올바르지 않아요"));
	}

	@Test
	@DisplayName("엉터리 JWT도 401")
	void brokenJwt() throws Exception {
		mvc.perform(get("/api/v1/analyses").header("Authorization", bearer("abc.def.ghi")))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("일반 사용자가 관리자 주소에 오면 403")
	void adminPathForbiddenForUser() throws Exception {
		mvc.perform(get("/api/v1/admin/misdetections").header("Authorization", bearer(deviceToken())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	@DisplayName("POST 전용 주소에 GET을 보내면 500이 아니라 405")
	void wrongMethod() throws Exception {
		mvc.perform(get("/api/v1/auth/refresh"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
	}
}
