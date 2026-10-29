package com.jiwoo.wastesorting_server.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

/** 오탐 원본 업로드: 오탐 판정된 내 박스 분석만, 한 번만, 박스 값 검사 */
class MisdetectionApiTest extends ApiTestSupport {

	private static final byte[] FAKE_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02 };
	private static final String BOX = "{\"box\":[0.5,0.5,0.2,0.3]}";

	private static MockMultipartFile image() {
		return new MockMultipartFile("image", "a.jpg", "image/jpeg", FAKE_JPEG);
	}

	private static MockMultipartFile meta(String json) {
		return new MockMultipartFile("meta", "", "text/plain", json.getBytes(StandardCharsets.UTF_8));
	}

	/** 박스 분석을 하고 analysisId를 돌려준다 (가짜 Gemini는 항상 pet) */
	private long analyze(String token, String detectedLabel) throws Exception {
		when(gemini.generate(any(), any(), any())).thenReturn(PET_JSON);
		String body = mvc.perform(multipart("/api/v1/analyses").file(image())
				.file(meta("{\"mode\":\"crop\",\"detectedLabel\":\"" + detectedLabel + "\",\"confidence\":0.6}"))
				.header("Authorization", token))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(body, "$.analysisId")).longValue();
	}

	@Test
	@DisplayName("오탐 분석은 한 번 올릴 수 있고, 두 번째는 409 ALREADY_UPLOADED")
	void uploadOnce() throws Exception {
		String token = bearer(deviceToken());
		long id = analyze(token, "can"); // can → pet : 오탐

		mvc.perform(multipart("/api/v1/analyses/" + id + "/frame").file(image()).file(meta(BOX))
				.header("Authorization", token))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.misdetectionId").isNumber());
		mvc.perform(multipart("/api/v1/analyses/" + id + "/frame").file(image()).file(meta(BOX))
				.header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ALREADY_UPLOADED"));
	}

	@Test
	@DisplayName("오탐이 아닌 분석은 409 NOT_MISDETECTION")
	void rejectsCorrectDetection() throws Exception {
		String token = bearer(deviceToken());
		long id = analyze(token, "pet"); // pet → pet : 정답

		mvc.perform(multipart("/api/v1/analyses/" + id + "/frame").file(image()).file(meta(BOX))
				.header("Authorization", token))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("NOT_MISDETECTION"));
	}

	@Test
	@DisplayName("남의 분석에는 올릴 수 없다 (404)")
	void rejectsOthersAnalysis() throws Exception {
		long id = analyze(bearer(deviceToken()), "can");

		mvc.perform(multipart("/api/v1/analyses/" + id + "/frame").file(image()).file(meta(BOX))
				.header("Authorization", bearer(deviceToken())))
				.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("박스 값이 0~1 밖이면 400 BAD_BOX")
	void rejectsBadBox() throws Exception {
		String token = bearer(deviceToken());
		long id = analyze(token, "can");

		mvc.perform(multipart("/api/v1/analyses/" + id + "/frame").file(image())
				.file(meta("{\"box\":[0.5,0.5,1.5,0.3]}"))
				.header("Authorization", token))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_BOX"));
	}
}
