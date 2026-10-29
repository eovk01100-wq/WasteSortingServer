package com.jiwoo.wastesorting_server.guide;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.jiwoo.wastesorting_server.gemini.DisposalResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 글자 검색 캐시 읽기·쓰기.
 * - 정해진 기간(기본 30일)이 지난 값은 없는 것으로 보고 Gemini에서 다시 받는다 (분리배출 기준이 바뀔 수 있어서)
 * - "확인 필요"(무엇인지 모름) 답은 캐시하지 않는다 (다음 검색 때 다시 물어볼 수 있게)
 */
@Component
public class GuideCache {

	private static final Logger log = LoggerFactory.getLogger(GuideCache.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final String UNKNOWN_CATEGORY = "확인 필요";

	private final GuideRepository guides;
	private final int ttlDays;

	public GuideCache(GuideRepository guides, @Value("${guide-cache.ttl-days:30}") int ttlDays) {
		this.guides = guides;
		this.ttlDays = ttlDays;
	}

	/** 아직 유효한 캐시가 있으면 돌려주고 사용 횟수를 1 올린다 */
	@Transactional
	public Optional<DisposalResponse> find(String normalizedQuery) {
		Optional<Guide> found = guides.findByQuery(normalizedQuery);
		if (found.isEmpty() || isExpired(found.get())) {
			return Optional.empty();
		}
		Guide g = found.get();
		g.hit();
		return Optional.of(toResponse(g));
	}

	/** Gemini에서 받은 답을 저장 (이미 있으면 새 내용으로 고침). 저장 충돌을 잡아야 해서 여기엔 트랜잭션을 걸지 않는다 */
	public void put(String normalizedQuery, DisposalResponse r) {
		if (r.category() != null && r.category().contains(UNKNOWN_CATEGORY)) {
			return;
		}
		Guide g = guides.findByQuery(normalizedQuery).orElseGet(() -> new Guide(normalizedQuery));
		g.refresh(r.itemName(), r.category(), JSON.writeValueAsString(r.steps() == null ? List.of() : r.steps()),
				r.caution(), r.note(), r.yoloClass());
		try {
			guides.saveAndFlush(g);
		} catch (DataIntegrityViolationException e) {
			// 같은 검색어가 동시에 두 번 들어와 다른 요청이 먼저 저장한 경우. 그쪽 값을 쓰면 되므로 무시
			log.debug("캐시 동시 저장 무시: {}", normalizedQuery);
		}
	}

	private boolean isExpired(Guide g) {
		return g.getUpdatedAt() == null || g.getUpdatedAt().isBefore(LocalDateTime.now().minusDays(ttlDays));
	}

	private static DisposalResponse toResponse(Guide g) {
		List<String> steps;
		try {
			steps = g.getStepsJson() == null ? List.of()
					: JSON.readValue(g.getStepsJson(), new TypeReference<List<String>>() {
					});
		} catch (JacksonException e) {
			steps = List.of();
		}
		return new DisposalResponse(g.getItemName(), g.getCategory(), steps, g.getCaution(), g.getNote(),
				g.getYoloClass());
	}
}
