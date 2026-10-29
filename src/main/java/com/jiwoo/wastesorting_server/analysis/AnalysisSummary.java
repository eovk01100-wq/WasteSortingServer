package com.jiwoo.wastesorting_server.analysis;

import java.time.LocalDateTime;

/** 기록 목록의 한 줄 */
public record AnalysisSummary(
		Long id,
		AnalysisSource source,
		String itemName,
		String category,
		String yoloClass,
		String detectedLabel,
		String query,
		boolean misdetection,
		LocalDateTime createdAt) {

	static AnalysisSummary of(Analysis a) {
		return new AnalysisSummary(a.getId(), a.getSource(), a.getItemName(), a.getCategory(), a.getYoloClass(),
				a.getDetectedLabel(), a.getQuery(), a.isMisdetection(), a.getCreatedAt());
	}
}
