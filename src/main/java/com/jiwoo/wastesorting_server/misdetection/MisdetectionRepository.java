package com.jiwoo.wastesorting_server.misdetection;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MisdetectionRepository extends JpaRepository<Misdetection, Long> {

	boolean existsByAnalysisId(Long analysisId);

	List<Misdetection> findTop200ByDeleteAfterBefore(LocalDateTime time);
}
