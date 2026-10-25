package com.jiwoo.wastesorting_server.gemini;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 앱에 돌려주는 분리배출 안내. 앱의 DisposalResponse와 필드 이름이 같다 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DisposalResponse(
		String itemName,
		String category,
		List<String> steps,
		String caution,
		String note,
		String yoloClass) {
}
