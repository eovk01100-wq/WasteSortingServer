package com.jiwoo.wastesorting_server.analysis;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

	Page<Analysis> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

	/** 본인 기록만 찾는다 (남의 기록이면 비어 있음 → 404) */
	Optional<Analysis> findByIdAndUserId(Long id, Long userId);

	/** 로그인할 때 이 기기의 익명 기록을 계정으로 옮긴다 */
	@Modifying
	@Query("update Analysis a set a.userId = :to where a.userId = :from")
	int moveAll(@Param("from") Long from, @Param("to") Long to);
}
