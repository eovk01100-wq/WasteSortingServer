package com.jiwoo.wastesorting_server.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.jiwoo.wastesorting_server.gemini.DisposalResponse;
import com.jiwoo.wastesorting_server.gemini.GeminiParser;
import com.jiwoo.wastesorting_server.guide.QueryNormalizer;

/** 서버를 띄우지 않는 빠른 단위 테스트 */
class GeminiParserTest {

	@Test
	@DisplayName("```json 코드블록으로 감싸 와도 읽는다")
	void stripsCodeFence() {
		DisposalResponse r = GeminiParser.parse("```json\n{\"itemName\":\"캔\",\"steps\":[\"헹군다\"],\"yoloClass\":\"can\"}\n```");
		assertThat(r.itemName()).isEqualTo("캔x");
		assertThat(r.yoloClass()).isEqualTo("can");
	}

	@Test
	@DisplayName("허용 목록 밖 분류는 null, 대문자는 소문자로")
	void normalizesClass() {
		assertThat(GeminiParser.parse("{\"itemName\":\"a\",\"yoloClass\":\"PET\"}").yoloClass()).isEqualTo("pet");
		assertThat(GeminiParser.parse("{\"itemName\":\"a\",\"yoloClass\":\"battery\"}").yoloClass()).isNull();
	}

	@Test
	@DisplayName("단계에 null·빈칸이 섞여도 걸러 내고 8개까지만")
	void cleansSteps() {
		DisposalResponse r = GeminiParser.parse(
				"{\"itemName\":\"a\",\"steps\":[\"1\",null,\" \",\"2\",\"3\",\"4\",\"5\",\"6\",\"7\",\"8\",\"9\"]}");
		assertThat(r.steps()).hasSize(8).doesNotContainNull().first().isEqualTo("1");
	}

	@Test
	@DisplayName("이름도 단계도 없으면 오류")
	void emptyAnswerFails() {
		assertThatThrownBy(() -> GeminiParser.parse("{\"steps\":[]}")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("검색어 정리: 앞뒤·연속 공백, 영문 대소문자")
	void queryNormalizer() {
		assertThat(QueryNormalizer.normalize("  PET   병 ")).isEqualTo("pet 병");
		assertThat(QueryNormalizer.normalize(null)).isEmpty();
	}
}
