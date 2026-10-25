package com.jiwoo.wastesorting_server.gemini;

/**
 * Gemini 호출 창구. 테스트에서는 가짜 구현으로 바꿔 끼워서 비용 없이 돌린다.
 * image가 null이면 글만 보낸다.
 */
public interface GeminiClient {

	/** Gemini가 돌려준 원문 글(JSON 문자열)을 그대로 돌려준다 */
	String generate(String prompt, byte[] image, String mimeType);
}
