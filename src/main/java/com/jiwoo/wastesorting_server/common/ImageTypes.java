package com.jiwoo.wastesorting_server.common;

import org.springframework.http.HttpStatus;

/** 확장자가 아니라 파일 앞부분(매직 바이트)으로 jpg/png만 허용 */
public final class ImageTypes {

	public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

	private ImageTypes() {
	}

	/** 크기·형식을 검사하고 MIME 타입을 돌려준다 */
	public static String check(byte[] b) {
		if (b == null || b.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE", "사진이 비어 있어요");
		}
		if (b.length > MAX_IMAGE_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "사진은 2MB 이하만 보낼 수 있어요");
		}
		if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return "image/png";
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_IMAGE_TYPE", "jpg나 png 사진만 보낼 수 있어요");
	}
}
