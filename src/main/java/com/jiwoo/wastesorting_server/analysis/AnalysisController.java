package com.jiwoo.wastesorting_server.analysis;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.multipart.MultipartFile;

import com.jiwoo.wastesorting_server.auth.TokenAuthFilter;
import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.common.ImageTypes;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@Tag(name = "분석", description = "사진 분석, 글자 검색, 내 기록")
@RequestMapping("/api/v1")
public class AnalysisController {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final AnalysisService service;

	public AnalysisController(AnalysisService service) {
		this.service = service;
	}

	/** 사진 분석: multipart로 image(jpg/png 파일)와 meta(JSON 글자)를 받는다 */
	@PostMapping(value = "/analyses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public AnalysisResponse analyze(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@RequestPart("image") MultipartFile image, @RequestPart("meta") String metaJson) throws IOException {
		AnalysisMeta meta;
		try {
			meta = JSON.readValue(metaJson, AnalysisMeta.class);
		} catch (JacksonException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_META", "meta가 올바른 JSON이 아니에요");
		}

		byte[] bytes = image.getBytes();
		String mimeType = ImageTypes.check(bytes);
		return service.analyzeImage(userId, meta, bytes, mimeType);
	}

	/** 글자 검색: 사진 없이 물건 이름으로 */
	@GetMapping("/search")
	public AnalysisResponse search(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@RequestParam("q") String q) {
		return service.search(userId, q);
	}

	/** 내 분석 기록 목록 (최신순). 예: /api/v1/analyses?page=0&size=20 */
	@GetMapping("/analyses")
	public PageResponse<AnalysisSummary> list(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return service.list(userId, page, size);
	}

	/** 내 분석 기록 한 건 (남의 기록이면 404) */
	@GetMapping("/analyses/{id}")
	public AnalysisDetail detail(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId, @PathVariable("id") Long id) {
		return service.detail(userId, id);
	}
}
