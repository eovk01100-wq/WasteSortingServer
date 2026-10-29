package com.jiwoo.wastesorting_server.misdetection;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jiwoo.wastesorting_server.analysis.PageResponse;
import com.jiwoo.wastesorting_server.auth.TokenAuthFilter;
import com.jiwoo.wastesorting_server.misdetection.AdminDtos.MisdetectionItem;
import com.jiwoo.wastesorting_server.misdetection.AdminDtos.ReviewRequest;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** 관리자 검수 (ADMIN만: SecurityConfig의 /api/v1/admin/** 규칙) */
@RestController
@RequestMapping("/api/v1/admin/misdetections")
@Tag(name = "관리자", description = "오탐 검수와 YOLO 학습 데이터 내보내기 (ADMIN만)")
public class AdminMisdetectionController {

	private final AdminMisdetectionService service;

	public AdminMisdetectionController(AdminMisdetectionService service) {
		this.service = service;
	}

	/** 오탐 목록 (최신순). status: PENDING(검수 대기) / CONFIRMED / REJECTED */
	@GetMapping
	public PageResponse<MisdetectionItem> list(
			@RequestParam(name = "status", defaultValue = "PENDING") MisdetectionStatus status,
			@RequestParam(name = "page", defaultValue = "0") int page,
			@RequestParam(name = "size", defaultValue = "20") int size) {
		return service.list(status, page, size);
	}

	/** 사진 보기. box=true면 탐지 박스를 빨간 테두리로 그려서 보여 준다 */
	@GetMapping("/{id}/image")
	public ResponseEntity<byte[]> image(@PathVariable("id") Long id,
			@RequestParam(name = "box", defaultValue = "true") boolean box) {
		return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).body(service.image(id, box));
	}

	/** 정답 확정 {"decision":"CONFIRM","label":"plastic"} 또는 제외 {"decision":"REJECT"} */
	@PatchMapping("/{id}")
	public MisdetectionItem review(@Parameter(hidden = true) @RequestAttribute(TokenAuthFilter.USER_ID) Long adminId,
			@PathVariable("id") Long id, @Valid @RequestBody ReviewRequest req) {
		return service.review(id, req.decision(), req.label(), adminId);
	}

	/** 정답이 확정된 사례를 YOLO 학습 형식 zip으로 내려받기 */
	@GetMapping("/export")
	public ResponseEntity<byte[]> export() {
		byte[] zip = service.exportZip();
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(AdminMisdetectionService.exportFileName()).build()
								.toString())
				.contentType(MediaType.parseMediaType("application/zip"))
				.body(zip);
	}
}
