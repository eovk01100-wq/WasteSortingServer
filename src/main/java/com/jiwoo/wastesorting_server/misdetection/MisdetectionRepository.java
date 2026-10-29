package com.jiwoo.wastesorting_server.misdetection;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MisdetectionRepository extends JpaRepository<Misdetection, Long> {

	boolean existsByAnalysisId(Long analysisId);

	List<Misdetection> findByUserId(Long userId);

	List<Misdetection> findTop200ByDeleteAfterBefore(LocalDateTime time);

	Page<Misdetection> findByStatusOrderByIdDesc(MisdetectionStatus status, Pageable pageable);

	List<Misdetection> findByStatusOrderByIdAsc(MisdetectionStatus status);
}
