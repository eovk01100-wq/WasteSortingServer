package com.jiwoo.wastesorting_server.analysis;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.gemini.DisposalResponse;
import com.jiwoo.wastesorting_server.gemini.GeminiClient;
import com.jiwoo.wastesorting_server.gemini.GeminiParser;
import com.jiwoo.wastesorting_server.gemini.GeminiPrompts;
import com.jiwoo.wastesorting_server.gemini.WasteClasses;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** 사진 분석과 글자 검색, 그리고 그 기록. 사진은 저장하지 않고 응답 후 버린다 */
@Service
public class AnalysisService {

	private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);
	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final int MAX_PAGE_SIZE = 50;

	private final GeminiClient gemini;
	private final AnalysisRepository analyses;

	public AnalysisService(GeminiClient gemini, AnalysisRepository analyses) {
		this.gemini = gemini;
		this.analyses = analyses;
	}

	// Gemini 응답을 기다리는 동안 DB 연결을 붙잡지 않도록 여기에는 @Transactional을 붙이지 않는다
	public AnalysisResponse analyzeImage(Long userId, AnalysisMeta meta, byte[] image, String mimeType) {
		String mode = meta.mode() == null ? "" : meta.mode().trim().toLowerCase(Locale.ROOT);
		AnalysisSource source;
		String label = null;
		Double conf = null;
		String prompt;
		if ("crop".equals(mode)) {
			label = WasteClasses.normalize(meta.detectedLabel());
			if (label == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_LABEL", "detectedLabel이 올바르지 않아요");
			}
			conf = meta.confidence() == null ? 0.0 : Math.max(0.0, Math.min(1.0, meta.confidence()));
			source = AnalysisSource.CROP;
			prompt = GeminiPrompts.crop(label, conf);
		} else if ("whole".equals(mode)) {
			source = AnalysisSource.WHOLE;
			prompt = GeminiPrompts.whole();
		} else {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_MODE", "mode는 crop 또는 whole이어야 해요");
		}

		long start = System.currentTimeMillis();
		DisposalResponse r = parse(gemini.generate(prompt, image, mimeType));
		long took = System.currentTimeMillis() - start;

		// 탐지 라벨과 AI 판단이 다르면 오탐 후보 (화면 전체 분석은 비교할 라벨이 없음)
		boolean misdetection = label != null && r.yoloClass() != null && !label.equals(r.yoloClass());
		Analysis saved = save(userId, source, label, conf, null, r, misdetection, took);
		log.info("사진 분석 #{} {} label={} → {} 오탐={} ({}ms)", saved.getId(), source, label, r.yoloClass(),
				misdetection, took);
		return toResponse(saved.getId(), r, misdetection);
	}

	public AnalysisResponse search(Long userId, String query) {
		String q = query == null ? "" : query.trim();
		if (q.isEmpty() || q.length() > 50) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_QUERY", "검색어는 1~50자로 입력해 주세요");
		}
		long start = System.currentTimeMillis();
		DisposalResponse r = parse(gemini.generate(GeminiPrompts.text(q), null, null));
		long took = System.currentTimeMillis() - start;
		Analysis saved = save(userId, AnalysisSource.SEARCH, null, null, q, r, false, took);
		log.info("글자 검색 #{} q={} ({}ms)", saved.getId(), q, took);
		return toResponse(saved.getId(), r, false);
	}

	@Transactional(readOnly = true)
	public PageResponse<AnalysisSummary> list(Long userId, int page, int size) {
		int p = Math.max(0, page);
		int s = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
		return PageResponse.of(analyses.findByUserIdOrderByIdDesc(userId, PageRequest.of(p, s))
				.map(AnalysisSummary::of));
	}

	@Transactional(readOnly = true)
	public AnalysisDetail detail(Long userId, Long id) {
		// 남의 기록이면 "없음"과 똑같이 404를 준다 (있는지조차 알려 주지 않기)
		Analysis a = analyses.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "기록을 찾을 수 없어요"));
		return new AnalysisDetail(a.getId(), a.getSource(), a.getDetectedLabel(), a.getConfidence(), a.getQuery(),
				a.getItemName(), a.getCategory(), readSteps(a.getStepsJson()), a.getCaution(), a.getNote(),
				a.getYoloClass(), a.isMisdetection(), a.getLatencyMs(), a.getCreatedAt());
	}

	private Analysis save(Long userId, AnalysisSource source, String label, Double conf, String query,
			DisposalResponse r, boolean misdetection, long took) {
		String stepsJson = JSON.writeValueAsString(r.steps() == null ? List.of() : r.steps());
		return analyses.save(new Analysis(userId, source, label, conf, query, r.itemName(), r.category(), stepsJson,
				r.caution(), r.note(), r.yoloClass(), misdetection, took));
	}

	private static List<String> readSteps(String json) {
		if (json == null || json.isBlank()) {
			return List.of();
		}
		try {
			return JSON.readValue(json, new TypeReference<List<String>>() {
			});
		} catch (JacksonException e) {
			return List.of();
		}
	}

	private static AnalysisResponse toResponse(Long id, DisposalResponse r, boolean misdetection) {
		return new AnalysisResponse(id, r.itemName(), r.category(), r.steps(), r.caution(), r.note(), r.yoloClass(),
				misdetection);
	}

	private DisposalResponse parse(String text) {
		try {
			return GeminiParser.parse(text);
		} catch (IllegalStateException e) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_BAD_RESPONSE", e.getMessage());
		}
	}
}
