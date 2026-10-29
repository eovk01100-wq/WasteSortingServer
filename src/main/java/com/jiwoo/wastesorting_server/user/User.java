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
 * 사용자. 앱 설치마다 익명 사용자가 생기고(기기 토큰), 원하면 이메일·비밀번호를 붙여 계정이 된다(선택 로그인).
 * 기기 토큰 원문은 저장하지 않고 SHA-256 해시만, 비밀번호는 BCrypt 해시만 저장한다.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "device_token_hash", nullable = false, unique = true, length = 64)
	private String deviceTokenHash;

	/** 계정으로 바꾼 경우에만 값이 있다 (익명 사용자는 null) */
	@Column(unique = true, length = 100)
	private String email;

	@Column(name = "password_hash", length = 100)
	private String passwordHash;

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

	/** 익명 사용자에 이메일·비밀번호를 붙여 계정으로 만든다 */
	public void registerAccount(String email, String passwordHash) {
		this.email = email;
		this.passwordHash = passwordHash;
	}

	public boolean isRegistered() {
		return email != null;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
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
