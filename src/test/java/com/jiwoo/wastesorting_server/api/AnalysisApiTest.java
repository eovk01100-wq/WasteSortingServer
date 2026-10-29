package com.jiwoo.wastesorting_server.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;

/** 사진 분석·글자 검색·기록 조회 */
class AnalysisApiTest extends ApiTestSupport {

	/** JPEG 파일의 앞 3바이트(FF D8 FF)만 맞춘 가짜 사진 */
	private static final byte[] FAKE_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02 };

	private static MockMultipartFile meta(String json) {
		return new MockMultipartFile("meta", "", "text/plain", json.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	@DisplayName("같은 검색어 두 번째는 캐시로 답하고 Gemini는 한 번만 부른다 (공백·대소문자 무시)")
	void searchCache() throws Exception {
		when(gemini.generate(contains("캐시테스트품목"), any(), any())).thenReturn(PET_JSON);
		String token = bearer(deviceToken());

		mvc.perform(get("/api/v1/search").param("q", "캐시테스트품목").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cached").value(false))
				.andExpect(jsonPath("$.analysisId").isNumber());
		mvc.perform(get("/api/v1/search").param("q", "  캐시테스트품목 ").header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.cached").value(true))
				.andExpect(jsonPath("$.itemName").value("투명 페트병"));

		verify(gemini, times(1)).generate(contains("캐시테스트품목"), any(), any());
	}

	@Test
	@DisplayName("탐지 라벨(can)과 AI 판단(pet)이 다르면 misdetection=true로 기록된다")
	void misdetectionIsRecorded() throws Exception {
		when(gemini.generate(any(), any(), any())).thenReturn(PET_JSON);
		String token = bearer(deviceToken());

		mvc.perform(multipart("/api/v1/analyses")
				.file(new MockMultipartFile("image", "a.jpg", "image/jpeg", FAKE_JPEG))
				.file(meta("{\"mode\":\"crop\",\"detectedLabel\":\"can\",\"confidence\":0.7}"))
				.header("Authorization", token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.yoloClass").value("pet"))
				.andExpect(jsonPath("$.misdetection").value(true));
	}

	@Test
	@DisplayName("jpg/png가 아닌 파일은 400 BAD_IMAGE_TYPE")
	void rejectsNonImage() throws Exception {
		mvc.perform(multipart("/api/v1/analyses")
				.file(new MockMultipartFile("image", "a.jpg", "image/jpeg", "not an image".getBytes()))
				.file(meta("{\"mode\":\"whole\"}"))
				.header("Authorization", bearer(deviceToken())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_IMAGE_TYPE"));
	}

	@Test
	@DisplayName("남의 기록은 403이 아니라 404 (있는지조차 숨김)")
	void otherUsersRecordIs404() throws Exception {
		when(gemini.generate(contains("남의기록테스트"), any(), any())).thenReturn(PET_JSON);
		String owner = bearer(deviceToken());
		String body = mvc.perform(get("/api/v1/search").param("q", "남의기록테스트").header("Authorization", owner))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		Number id = JsonPath.read(body, "$.analysisId");

		mvc.perform(get("/api/v1/analyses/" + id).header("Authorization", owner))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.query").value("남의기록테스트"));
		mvc.perform(get("/api/v1/analyses/" + id).header("Authorization", bearer(deviceToken())))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
