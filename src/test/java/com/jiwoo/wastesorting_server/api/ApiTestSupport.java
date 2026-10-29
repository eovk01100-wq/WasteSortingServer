package com.jiwoo.wastesorting_server.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.jiwoo.wastesorting_server.gemini.GeminiClient;

/** API 테스트 공통: 전체 서버를 띄우고(메모리 DB), Gemini만 가짜로 바꾼다 */
@SpringBootTest
@ActiveProfiles("test")
abstract class ApiTestSupport {

	/** 가짜 Gemini가 돌려줄 정상 답 */
	static final String PET_JSON = """
			{"itemName":"투명 페트병","category":"투명 페트병","steps":["헹군다","라벨을 뗀다"],
			 "caution":"","note":"","yoloClass":"pet"}""";

	@Autowired
	WebApplicationContext context;

	@MockitoBean
	GeminiClient gemini;

	MockMvc mvc;

	@BeforeEach
	void setUpMockMvc() {
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
	}

	/** 새 익명 기기 토큰 받기 */
	String deviceToken() throws Exception {
		String body = mvc.perform(post("/api/v1/auth/device"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	static String bearer(String token) {
		return "Bearer " + token;
	}

	static MediaType json() {
		return MediaType.APPLICATION_JSON;
	}
}
