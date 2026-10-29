package com.jiwoo.wastesorting_server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.jayway.jsonpath.JsonPath;
import com.jiwoo.wastesorting_server.auth.TokenHasher;
import com.jiwoo.wastesorting_server.gemini.GeminiClient;

/**
 * 실제 MySQL 8.4(배포와 같은 버전)를 Docker로 띄워 전체 흐름을 한 번에 확인한다.
 * 익명 토큰 → 박스 분석(오탐) → 원본 업로드 → 관리자 검수 → YOLO zip → 탈퇴
 * Docker가 없으면 건너뛴다 (H2로 도는 다른 테스트는 그대로 실행).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class FullFlowIntegrationTest {

	@Container
	@ServiceConnection
	static MySQLContainer mysql = new MySQLContainer("mysql:8.4");

	private static final byte[] FAKE_JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02 };
	private static final String PET_JSON = """
			{"itemName":"투명 페트병","category":"투명 페트병","steps":["헹군다"],"caution":"","note":"","yoloClass":"pet"}""";

	@Autowired
	WebApplicationContext context;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	GeminiClient gemini;

	@Test
	@DisplayName("MySQL에서 분석 → 오탐 업로드 → 검수 → zip → 탈퇴까지 이어진다")
	void fullFlowOnMySql() throws Exception {
		MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		when(gemini.generate(any(), any(), any())).thenReturn(PET_JSON);

		// 1) 사용자: 익명 토큰 → 박스 분석 (탐지 can, AI pet → 오탐)
		String userRaw = token(mvc);
		String user = "Bearer " + userRaw;
		String analysis = mvc.perform(multipart("/api/v1/analyses")
				.file(file("image", FAKE_JPEG))
				.file(file("meta", "{\"mode\":\"crop\",\"detectedLabel\":\"can\",\"confidence\":0.6}".getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.misdetection").value(true))
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		long analysisId = ((Number) JsonPath.read(analysis, "$.analysisId")).longValue();

		// 2) 동의한 사용자가 원본 화면 업로드
		String up = mvc.perform(multipart("/api/v1/analyses/" + analysisId + "/frame")
				.file(file("image", FAKE_JPEG))
				.file(file("meta", "{\"box\":[0.5,0.5,0.25,0.25]}".getBytes(StandardCharsets.UTF_8)))
				.header("Authorization", user))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		long misId = ((Number) JsonPath.read(up, "$.misdetectionId")).longValue();

		// 3) 관리자: 정답 확정 → zip
		String adminRaw = token(mvc);
		jdbc.update("update users set role = 'ADMIN' where device_token_hash = ?", TokenHasher.sha256(adminRaw));
		String admin = "Bearer " + adminRaw;
		mvc.perform(patch("/api/v1/admin/misdetections/" + misId).header("Authorization", admin)
				.contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRM\",\"label\":\"pet\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));
		byte[] zip = mvc.perform(get("/api/v1/admin/misdetections/export").header("Authorization", admin))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(zip.length).isGreaterThan(0);

		// 4) 사용자 탈퇴 → 기록·오탐이 MySQL에서 사라진다
		mvc.perform(delete("/api/v1/users/me").header("Authorization", user))
				.andExpect(status().isNoContent());
		assertThat(jdbc.queryForObject("select count(*) from analysis where id = ?", Integer.class, analysisId))
				.isZero();
		assertThat(jdbc.queryForObject("select count(*) from misdetection where id = ?", Integer.class, misId))
				.isZero();
	}

	private static String token(MockMvc mvc) throws Exception {
		String body = mvc.perform(post("/api/v1/auth/device"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.token");
	}

	private static MockMultipartFile file(String name, byte[] data) {
		return "image".equals(name)
				? new MockMultipartFile(name, "a.jpg", "image/jpeg", data)
				: new MockMultipartFile(name, "", "text/plain", data);
	}
}
