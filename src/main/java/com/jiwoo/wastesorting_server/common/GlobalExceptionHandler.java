package com.jiwoo.wastesorting_server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** 컨트롤러에서 난 오류를 { code, message } 형식으로 통일 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ErrorResponse> handleTooLarge(MaxUploadSizeExceededException e) {
		return ResponseEntity.badRequest()
				.body(new ErrorResponse("IMAGE_TOO_LARGE", "사진은 2MB 이하만 보낼 수 있어요"));
	}

	@ExceptionHandler({ MissingServletRequestPartException.class, MissingServletRequestParameterException.class })
	public ResponseEntity<ErrorResponse> handleMissing(Exception e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "필요한 값이 빠졌어요: " + e.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleOther(Exception e) {
		log.error("처리하지 못한 오류", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("SERVER_ERROR", "서버에서 오류가 났어요"));
	}
}
