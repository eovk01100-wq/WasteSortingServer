package com.jiwoo.wastesorting_server.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByDeviceTokenHash(String deviceTokenHash);

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
