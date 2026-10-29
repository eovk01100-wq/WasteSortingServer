package com.jiwoo.wastesorting_server.analysis;

import java.util.List;

/**
 * 분석·검색 응답. 앱이 쓰던 6개 필드는 그대로이고,
 * analysisId(기록 번호), misdetection(오탐 여부), cached(저장된 답을 썼는지)가 추가됐다. 앱은 모르는 필드를 무시한다.
 */
public record AnalysisResponse(
		Long analysisId,
		String itemName,
		String category,
		List<String> steps,
		String caution,
		String note,
		String yoloClass,
		boolean misdetection,
		boolean cached) {
}
