package com.jiwoo.wastesorting_server.analysis;

import java.util.List;

import org.springframework.data.domain.Page;

/** 페이지 응답 모양을 직접 정해 둔다 (Spring 내부 Page 모양에 앱이 묶이지 않게) */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

	static <T> PageResponse<T> of(Page<T> p) {
		return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
	}
}
