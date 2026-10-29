package com.jiwoo.wastesorting_server.guide;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuideRepository extends JpaRepository<Guide, Long> {

	Optional<Guide> findByQuery(String query);
}
