package com.jiwoo.wastesorting_server.common;

import org.springframework.http.HttpStatus;

/** 앱에 보낼 오류. code는 앱이 구분하는 값, message는 사람이 읽는 설명 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String code;

	public ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
