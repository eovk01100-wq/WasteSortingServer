package com.jiwoo.wastesorting_server.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/** 리프레시 토큰. 원문은 앱에만 있고 DB에는 SHA-256 해시만. 한 번 쓰면 지우고 새로 발급(회전) */
@Entity
@Table(name = "refresh_token", indexes = @Index(name = "idx_refresh_user", columnList = "user_id"))
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	protected RefreshToken() {
	}

	public RefreshToken(Long userId, String tokenHash, LocalDateTime expiresAt) {
		this.userId = userId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isExpired() {
		return expiresAt.isBefore(LocalDateTime.now());
	}

	public Long getUserId() {
		return userId;
	}
}
