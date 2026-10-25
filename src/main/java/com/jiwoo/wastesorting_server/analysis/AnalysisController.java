package com.jiwoo.wastesorting_server.analysis;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.gemini.DisposalResponse;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

	private final AnalysisService service;

	public AnalysisController(AnalysisService service) {
		this.service = service;
	}

	/** 사진 분석: multipart로 image(jpg/png 파일)와 meta(JSON 글자)를 받는다 */
	@PostMapping(value = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public DisposalResponse analyze(@RequestPart("image") MultipartFile image, @RequestPart("meta") String metaJson)
			throws IOException {
		AnalysisMeta meta;
		try {
			meta = JSON.readValue(metaJson, AnalysisMeta.class);
		} catch (JacksonException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_META", "meta가 올바른 JSON이 아니에요");
		}

		byte[] bytes = image.getBytes();
		if (bytes.length == 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_IMAGE", "사진이 비어 있어요");
		}
		if (bytes.length > MAX_IMAGE_BYTES) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "IMAGE_TOO_LARGE", "사진은 2MB 이하만 보낼 수 있어요");
		}
		String mimeType = detectImageType(bytes);
		return service.analyzeImage(meta, bytes, mimeType);
	}

	/** 글자 검색: 사진 없이 물건 이름으로 */
	@GetMapping("/search")
	public DisposalResponse search(@RequestParam("q") String q) {
		return service.search(q);
	}

	/** 확장자가 아니라 파일 앞부분(매직 바이트)으로 jpg/png만 허용 */
	private static String detectImageType(byte[] b) {
		if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
			return "image/png";
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_IMAGE_TYPE", "jpg나 png 사진만 보낼 수 있어요");
	}
}
