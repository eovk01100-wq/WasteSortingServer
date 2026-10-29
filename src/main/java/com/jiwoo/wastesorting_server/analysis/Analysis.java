package com.jiwoo.wastesorting_server.analysis;

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
 * 분석 기록 한 건. 사진은 저장하지 않고 결과만 남긴다.
 * misdetection: 사진 분석에서 탐지 모델 라벨과 AI 판단(yoloClass)이 다르면 true (3주차 오탐 수집의 출발점)
 */
@Entity
@Table(name = "analysis", indexes = @Index(name = "idx_analysis_user_created", columnList = "user_id, created_at"))
public class Analysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AnalysisSource source;

	@Column(name = "detected_label", length = 20)
	private String detectedLabel;

	private Double confidence;

	@Column(length = 50)
	private String query;

	@Column(name = "item_name", length = 60)
	private String itemName;

	@Column(length = 40)
	private String category;

	/** 단계 목록을 JSON 배열 글자로 저장 */
	@Column(name = "steps_json", columnDefinition = "TEXT")
	private String stepsJson;

	@Column(length = 300)
	private String caution;

	@Column(length = 300)
	private String note;

	@Column(name = "yolo_class", length = 10)
	private String yoloClass;

	@Column(nullable = false)
	private boolean misdetection;

	/** 글자 검색에서 캐시로 답했는지 (기존 행은 false로 채워짐) */
	@Column(nullable = false, columnDefinition = "boolean default false")
	private boolean cached;

	@Column(name = "latency_ms", nullable = false)
	private long latencyMs;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected Analysis() {
	}

	public Analysis(Long userId, AnalysisSource source, String detectedLabel, Double confidence, String query,
			String itemName, String category, String stepsJson, String caution, String note, String yoloClass,
			boolean misdetection, boolean cached, long latencyMs) {
		this.userId = userId;
		this.source = source;
		this.detectedLabel = detectedLabel;
		this.confidence = confidence;
		this.query = query;
		this.itemName = itemName;
		this.category = category;
		this.stepsJson = stepsJson;
		this.caution = caution;
		this.note = note;
		this.yoloClass = yoloClass;
		this.misdetection = misdetection;
		this.cached = cached;
		this.latencyMs = latencyMs;
		this.createdAt = LocalDateTime.now();
	}

	public Long getId() { return id; }
	public Long getUserId() { return userId; }
	public AnalysisSource getSource() { return source; }
	public String getDetectedLabel() { return detectedLabel; }
	public Double getConfidence() { return confidence; }
	public String getQuery() { return query; }
	public String getItemName() { return itemName; }
	public String getCategory() { return category; }
	public String getStepsJson() { return stepsJson; }
	public String getCaution() { return caution; }
	public String getNote() { return note; }
	public String getYoloClass() { return yoloClass; }
	public boolean isMisdetection() { return misdetection; }
	public boolean isCached() { return cached; }
	public long getLatencyMs() { return latencyMs; }
	public LocalDateTime getCreatedAt() { return createdAt; }
}
