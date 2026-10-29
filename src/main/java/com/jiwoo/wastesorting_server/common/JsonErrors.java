package com.jiwoo.wastesorting_server.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/** 필터처럼 컨트롤러 밖에서 { code, message } 오류를 바로 쓸 때 사용 (message는 코드에 고정된 문장만) */
public final class JsonErrors {

	private JsonErrors() {
	}

	public static void write(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
