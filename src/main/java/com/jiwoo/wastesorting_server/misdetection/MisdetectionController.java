package com.jiwoo.wastesorting_server.misdetection;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.jiwoo.wastesorting_server.auth.TokenAuthFilter;
import com.jiwoo.wastesorting_server.common.ApiException;
import com.jiwoo.wastesorting_server.common.ImageTypes;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "오탐", description = "사용자가 동의한 오탐 원본 화면 업로드 (YOLO 재학습용)")
public class MisdetectionController {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final MisdetectionService service;

	public MisdetectionController(MisdetectionService service) {
		this.service = service;
	}

	/**
	 * 오탐 원본 업로드: image = 원본 화면 전체, meta = {"box":[중심x, 중심y, 너비, 높이]} (0~1)
	 * 앱은 사용자가 '학습 자료 보내기'를 켠 경우에만 호출한다.
	 */
	@PostMapping(value = "/analyses/{id}/frame", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public UploadResponse upload(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long userId,
			@PathVariable("id") Long analysisId, @RequestPart("image") MultipartFile image,
			@RequestPart("meta") String metaJson) throws IOException {
		FrameMeta meta;
		try {
			meta = JSON.readValue(metaJson, FrameMeta.class);
		} catch (JacksonException e) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "BAD_META", "meta가 올바른 JSON이 아니에요");
		}
		byte[] bytes = image.getBytes();
		String mimeType = ImageTypes.check(bytes);
		return new UploadResponse(service.upload(userId, analysisId, bytes, mimeType, meta.box()));
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record FrameMeta(double[] box) {
	}

	public record UploadResponse(Long misdetectionId) {
	}
}
