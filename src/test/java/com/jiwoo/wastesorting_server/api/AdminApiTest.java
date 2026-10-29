package com.jiwoo.wastesorting_server.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.jiwoo.wastesorting_server.auth.TokenHasher;

/** 관리자 검수: 정답 확정 → YOLO zip에 라벨이 들어간다 */
class AdminApiTest extends ApiTestSupport {

	private static final byte[] FAKE_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02 };

	@Autowired
	JdbcTemplate jdbc;

	private String adminToken() throws Exception {
		String raw = deviceToken();
		jdbc.update("update users set role = 'ADMIN' where device_token_hash = ?", TokenHasher.sha256(raw));
		return bearer(raw);
	}

	/** 사용자가 can으로 탐지 → AI는 pet → 원본 업로드. 오탐 id를 돌려준다 */
	private long uploadedMisdetection() throws Exception {
		when(gemini.generate(any(), any(), any())).thenReturn(PET_JSON);
		String user = bearer(deviceToken());
		String body = mvc.perform(multipart("/api/v1/analyses")
				.file(new MockMultipartFile("image", "a.jpg", "image/jpeg", FAKE_JPEG))
				.file(new MockMultipartFile("meta", "", "text/plain",
						"{\"mode\":\"crop\",\"detectedLabel\":\"can\",\"confidence\":0.6}".getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", user))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		long analysisId = ((Number) JsonPath.read(body, "$.analysisId")).longValue();
		String up = mvc.perform(multipart("/api/v1/analyses/" + analysisId + "/frame")
				.file(new MockMultipartFile("image", "f.jpg", "image/jpeg", FAKE_JPEG))
				.file(new MockMultipartFile("meta", "", "text/plain",
						"{\"box\":[0.5,0.4,0.2,0.3]}".getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", user))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return ((Number) JsonPath.read(up, "$.misdetectionId")).longValue();
	}

	@Test
	@DisplayName("검수 대기 목록에 보이고, 정답(pet)을 확정하면 zip에 '4 cx cy w h' 라벨이 들어간다")
	void confirmAndExport() throws Exception {
		long id = uploadedMisdetection();
		String admin = adminToken();

		mvc.perform(get("/api/v1/admin/misdetections").header("Authorization", admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(id))
				.andExpect(jsonPath("$.content[0].detectedLabel").value("can"));

		mvc.perform(patch("/api/v1/admin/misdetections/" + id).header("Authorization", admin)
				.contentType(json()).content("{\"decision\":\"CONFIRM\",\"label\":\"pet\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.confirmedLabel").value("pet"));

		byte[] zip = mvc.perform(get("/api/v1/admin/misdetections/export").header("Authorization", admin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		Map<String, String> entries = new HashMap<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
			ZipEntry e;
			while ((e = in.getNextEntry()) != null) {
				entries.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
		}
		assertThat(entries).containsKeys("images/" + id + ".jpg", "data.yaml");
		assertThat(entries.get("labels/" + id + ".txt")).startsWith("4 0.500000 0.400000 0.200000 0.300000");
	}

	@Test
	@DisplayName("허용 목록 밖 정답은 400 BAD_LABEL")
	void rejectsUnknownLabel() throws Exception {
		long id = uploadedMisdetection();
		mvc.perform(patch("/api/v1/admin/misdetections/" + id).header("Authorization", adminToken())
				.contentType(json()).content("{\"decision\":\"CONFIRM\",\"label\":\"battery\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("BAD_LABEL"));
	}

	@Test
	@DisplayName("일반 사용자는 검수할 수 없다 (403)")
	void userCannotReview() throws Exception {
		long id = uploadedMisdetection();
		mvc.perform(patch("/api/v1/admin/misdetections/" + id).header("Authorization", bearer(deviceToken()))
				.contentType(json()).content("{\"decision\":\"REJECT\"}"))
				.andExpect(status().isForbidden());
	}
}
