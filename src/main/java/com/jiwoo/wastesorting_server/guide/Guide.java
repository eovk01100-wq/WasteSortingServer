package com.jiwoo.wastesorting_server.guide;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 글자 검색 결과 캐시. 같은 검색어가 다시 오면 Gemini를 부르지 않고 이 값을 돌려준다.
 * 사진 분석은 같은 분류라도 물건이 달라(예: 테이프 붙은 통) 캐시하지 않는다.
 */
@Entity
@Table(name = "guide", uniqueConstraints = @UniqueConstraint(name = "uk_guide_query", columnNames = "query"))
public class Guide {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** 정리된 검색어 (앞뒤 공백 제거, 연속 공백 하나로, 영문 소문자) */
	@Column(nullable = false, length = 50)
	private String query;

	@Column(name = "item_name", length = 60)
	private String itemName;

	@Column(length = 40)
	private String category;

	@Column(name = "steps_json", columnDefinition = "TEXT")
	private String stepsJson;

	@Column(length = 300)
	private String caution;

	@Column(length = 300)
	private String note;

	@Column(name = "yolo_class", length = 10)
	private String yoloClass;

	/** 캐시로 답한 횟수 (인기 검색어 통계용) */
	@Column(name = "hit_count", nullable = false)
	private long hitCount;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	/** 마지막으로 Gemini에서 새로 받은 시각. 오래되면 다시 받는다 */
	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	protected Guide() {
	}

	public Guide(String query) {
		this.query = query;
		this.createdAt = LocalDateTime.now();
	}

	/** Gemini에서 새로 받은 내용으로 채우거나 고친다 */
	public void refresh(String itemName, String category, String stepsJson, String caution, String note,
			String yoloClass) {
		this.itemName = itemName;
		this.category = category;
		this.stepsJson = stepsJson;
		this.caution = caution;
		this.note = note;
		this.yoloClass = yoloClass;
		this.updatedAt = LocalDateTime.now();
	}

	public void hit() {
		this.hitCount++;
	}

	public Long getId() { return id; }
	public String getQuery() { return query; }
	public String getItemName() { return itemName; }
	public String getCategory() { return category; }
	public String getStepsJson() { return stepsJson; }
	public String getCaution() { return caution; }
	public String getNote() { return note; }
	public String getYoloClass() { return yoloClass; }
	public long getHitCount() { return hitCount; }
	public LocalDateTime getUpdatedAt() { return updatedAt; }
}
