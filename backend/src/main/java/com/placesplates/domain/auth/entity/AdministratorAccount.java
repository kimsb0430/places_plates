package com.placesplates.domain.auth.entity;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_users")
public class AdministratorAccount {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 320)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountRole role;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false, insertable = false)
	private OffsetDateTime updatedAt;

	protected AdministratorAccount() {
	}

	private AdministratorAccount(String email, String passwordHash) {
		this.id = UUID.randomUUID();
		this.email = email.trim().toLowerCase(Locale.ROOT);
		this.passwordHash = passwordHash;
		this.status = AccountStatus.ACTIVE;
		this.role = AccountRole.ADMIN;
	}

	public static AdministratorAccount create(String email, String passwordHash) {
		return new AdministratorAccount(email, passwordHash);
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public AccountStatus getStatus() {
		return status;
	}

	public AccountRole getRole() {
		return role;
	}
}
