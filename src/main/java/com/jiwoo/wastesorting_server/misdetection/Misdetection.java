package com.jiwoo.wastesorting_server.misdetection;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * 오탐 사례 하나. 사용자가 동의해서 올린 "원본 화면 전체 + 박스 좌표"를 담는다.
 * YOLO 재학습에는 잘라낸 사진이 아니라 전체 화면과 박스가 필요하기 때문.
 * 박스는 YOLO 형식(0~1 비율): 중심 x, 중심 y, 너비, 높이.
 */
@Entity
@Table(name = "misdetection", indexes = {
		@Index(name = "idx_mis_status", columnList = "status"),
		@Index(name = "idx_mis_delete_after", columnList = "delete_after") })
public class Misdetection {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 분석 한 건당 오탐 사례는 하나 */
	@Column(name = "analysis_id", nullable = false, unique = true)
	private Long analysisId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	/** 탐지 모델이 붙인 라벨 */
	@Column(name = "detected_label", nullable = false, length = 10)
	private String detectedLabel;

	/** AI(Gemini)가 판단한 분류 */
	@Column(name = "ai_label", length = 10)
	private String aiLabel;

	/** 관리자가 확정한 정답 (3-2 검수에서 채움) */
	@Column(name = "confirmed_label", length = 10)
	private String confirmedLabel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private MisdetectionStatus status;

	/** 업로드 폴더 기준 파일 이름 (UUID.jpg). 경로 전체는 저장하지 않는다 */
	@Column(name = "frame_file", nullable = false, length = 60)
	private String frameFile;

	@Column(name = "box_cx", nullable = false)
	private double boxCx;

	@Column(name = "box_cy", nullable = false)
	private double boxCy;

	@Column(name = "box_w", nullable = false)
	private double boxW;

	@Column(name = "box_h", nullable = false)
	private double boxH;

	/** 사용자가 업로드(동의)한 시각 */
	@Column(name = "consent_at", nullable = false)
	private LocalDateTime consentAt;

	@Column(name = "reviewed_at")
	private LocalDateTime reviewedAt;

	/** 이 시각이 지나면 사진과 기록을 지운다 (보관 기간) */
	@Column(name = "delete_after", nullable = false)
	private LocalDateTime deleteAfter;

	protected Misdetection() {
	}

	public Misdetection(Long analysisId, Long userId, String detectedLabel, String aiLabel, String frameFile,
			double[] box, LocalDateTime deleteAfter) {
		this.analysisId = analysisId;
		this.userId = userId;
		this.detectedLabel = detectedLabel;
		this.aiLabel = aiLabel;
		this.frameFile = frameFile;
		this.boxCx = box[0];
		this.boxCy = box[1];
		this.boxW = box[2];
		this.boxH = box[3];
		this.status = MisdetectionStatus.PENDING;
		this.consentAt = LocalDateTime.now();
		this.deleteAfter = deleteAfter;
	}

	public Long getId() { return id; }
	public Long getAnalysisId() { return analysisId; }
	public Long getUserId() { return userId; }
	public String getDetectedLabel() { return detectedLabel; }
	public String getAiLabel() { return aiLabel; }
	public String getConfirmedLabel() { return confirmedLabel; }
	public MisdetectionStatus getStatus() { return status; }
	public String getFrameFile() { return frameFile; }
	public double getBoxCx() { return boxCx; }
	public double getBoxCy() { return boxCy; }
	public double getBoxW() { return boxW; }
	public double getBoxH() { return boxH; }
	public LocalDateTime getConsentAt() { return consentAt; }
	public LocalDateTime getReviewedAt() { return reviewedAt; }
	public LocalDateTime getDeleteAfter() { return deleteAfter; }
}
