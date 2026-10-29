package com.jiwoo.wastesorting_server.analysis;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	Page<Analysis> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

	/** 본인 기록만 찾는다 (남의 기록이면 비어 있음 → 404) */
	Optional<Analysis> findByIdAndUserId(Long id, Long userId);
}
