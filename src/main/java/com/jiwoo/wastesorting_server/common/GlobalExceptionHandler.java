package com.jiwoo.wastesorting_server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 컨트롤러에서 난 오류를 { code, message } 형식으로 통일 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
		return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getCode(), e.getMessage()));
	}

	/** @Valid 검사 실패 → 첫 번째 이유를 그대로 보여 준다 (예: "비밀번호는 8~64자로 정해 주세요") */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
		String msg = e.getBindingResult().getFieldErrors().stream()
				.map(f -> f.getDefaultMessage())
				.findFirst().orElse("입력값을 확인해 주세요");
		return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION", msg));
	}

	/** GET/POST를 잘못 고른 경우 → 500이 아니라 405로 알려 준다 */
	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
				.body(new ErrorResponse("METHOD_NOT_ALLOWED", e.getMethod() + " 방식은 이 주소에서 쓸 수 없어요"));
	}

	/** 주소 값의 형식이 틀린 경우 (예: status=WAITING, id=abc) */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", e.getName() + " 값이 올바르지 않아요"));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
		return ResponseEntity.badRequest().body(new ErrorResponse("BAD_REQUEST", "요청 본문(JSON)을 읽지 못했어요"));
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

	/** 없는 주소 (브라우저가 자동으로 찾는 favicon.ico 등) → 오류 로그 없이 404 */
	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("NOT_FOUND", "없는 주소예요"));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleOther(Exception e) {
		log.error("처리하지 못한 오류", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new ErrorResponse("SERVER_ERROR", "서버에서 오류가 났어요"));
	}
}
