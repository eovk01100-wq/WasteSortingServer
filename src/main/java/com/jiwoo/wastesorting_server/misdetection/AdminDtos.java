package com.jiwoo.wastesorting_server.misdetection;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotNull;

/** 관리자 검수 요청·응답 모양 */
public final class AdminDtos {

	private AdminDtos() {
	}

	public enum Decision {
		CONFIRM, REJECT
	}

	/** decision=CONFIRM이면 label(7개 분류 또는 other)이 필요하다. REJECT면 label 없음 */
	public record ReviewRequest(@NotNull(message = "decision(CONFIRM 또는 REJECT)이 필요해요") Decision decision,
			String label) {
	}

	public record MisdetectionItem(Long id, Long analysisId, String detectedLabel, String aiLabel,
			String confirmedLabel, MisdetectionStatus status, double[] box, LocalDateTime consentAt,
			LocalDateTime reviewedAt, LocalDateTime deleteAfter, String imageUrl) {

		static MisdetectionItem of(Misdetection m) {
			return new MisdetectionItem(m.getId(), m.getAnalysisId(), m.getDetectedLabel(), m.getAiLabel(),
					m.getConfirmedLabel(), m.getStatus(),
					new double[] { m.getBoxCx(), m.getBoxCy(), m.getBoxW(), m.getBoxH() }, m.getConsentAt(),
					m.getReviewedAt(), m.getDeleteAfter(), "/api/v1/admin/misdetections/" + m.getId() + "/image");
		}
	}
}
