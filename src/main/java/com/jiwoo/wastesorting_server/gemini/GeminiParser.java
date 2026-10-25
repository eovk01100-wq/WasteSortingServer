package com.jiwoo.wastesorting_server.gemini;

import java.util.List;
import java.util.Objects;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Gemini가 보낸 글을 앱에 줄 답변으로 바꾼다 (앱의 GeminiParser.kt와 같은 규칙) */
public final class GeminiParser {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private GeminiParser() {
	}

	public static DisposalResponse parse(String text) {
		if (text == null || text.isBlank()) {
			throw new IllegalStateException("AI 답변이 비어 있어요");
		}
		// 혹시 ```json ... ``` 으로 감싸서 오면 벗겨내기
		String json = text.trim();
		if (json.startsWith("```json")) {
			json = json.substring(7);
		} else if (json.startsWith("```")) {
			json = json.substring(3);
		}
		if (json.endsWith("```")) {
			json = json.substring(0, json.length() - 3);
		}
		json = json.trim();

		DisposalResponse parsed;
		try {
			parsed = JSON.readValue(json, DisposalResponse.class);
		} catch (JacksonException e) {
			throw new IllegalStateException("AI 답변을 읽지 못했어요", e);
		}
		if (parsed == null) {
			throw new IllegalStateException("AI 답변을 읽지 못했어요");
		}
		return sanitize(parsed);
	}

	/** 빈 답변은 오류, 너무 긴 글은 자르고, 분류는 허용된 값만 */
	public static DisposalResponse sanitize(DisposalResponse r) {
		List<String> steps = (r.steps() == null ? List.<String>of() : r.steps()).stream()
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.limit(8)
				.map(s -> cut(s, 200))
				.toList();
		if ((r.itemName() == null || r.itemName().isBlank()) && steps.isEmpty()) {
			throw new IllegalStateException("AI 답변이 비어 있어요");
		}
		return new DisposalResponse(
				cut(trim(r.itemName()), 60),
				cut(trim(r.category()), 40),
				steps,
				cut(trim(r.caution()), 300),
				cut(trim(r.note()), 300),
				WasteClasses.normalize(r.yoloClass()));
	}

	private static String trim(String s) {
		return s == null ? null : s.trim();
	}

	private static String cut(String s, int max) {
		return (s == null || s.length() <= max) ? s : s.substring(0, max);
	}
}
