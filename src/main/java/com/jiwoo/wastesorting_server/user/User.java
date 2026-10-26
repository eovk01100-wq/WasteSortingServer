package com.jiwoo.wastesorting_server.user;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 사용자. 1주차에는 앱 설치마다 생기는 익명 사용자만 있다.
 * 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * (이메일·비밀번호 칸은 2주차 선택 로그인에서 추가)
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "device_token_hash", nullable = false, unique = true, length = 64)
	private String deviceTokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Role role;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected User() {
	}

	public static User anonymous(String deviceTokenHash) {
		User u = new User();
		u.deviceTokenHash = deviceTokenHash;
		u.role = Role.USER;
		u.createdAt = LocalDateTime.now();
		return u;
	}

	public Long getId() {
		return id;
	}

	public String getDeviceTokenHash() {
		return deviceTokenHash;
	}

	public Role getRole() {
		return role;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}
}
