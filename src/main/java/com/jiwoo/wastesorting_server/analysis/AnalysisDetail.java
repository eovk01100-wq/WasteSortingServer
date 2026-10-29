package com.jiwoo.wastesorting_server.analysis;

import java.time.LocalDateTime;
import java.util.List;

/** 기록 한 건 상세 */
public record AnalysisDetail(
		Long id,
		AnalysisSource source,
		String detectedLabel,
		Double confidence,
		String query,
		String itemName,
		String category,
		List<String> steps,
		String caution,
		String note,
		String yoloClass,
		boolean misdetection,
		boolean cached,
		long latencyMs,
		LocalDateTime createdAt) {
}
