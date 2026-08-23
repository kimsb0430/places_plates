package com.placesplates.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseOwnerScopeFilterTests {

	@Test
	void publicApiUsesPublicDatabaseMode() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/public/posts")).isTrue();
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/public/posts"))
			.isEqualTo(DatabaseAccessMode.PUBLIC);
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/public"))
			.isEqualTo(DatabaseAccessMode.PUBLIC);
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/publicity"))
			.isEqualTo(DatabaseAccessMode.OWNER);
	}

	@Test
	void protectedApiUsesOwnerDatabaseMode() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/manage/posts")).isTrue();
		assertThat(DatabaseOwnerScopeFilter.resolveAccessMode("/api/v1/manage/posts"))
			.isEqualTo(DatabaseAccessMode.OWNER);
	}

	@Test
	void authenticationAndHealthApisDoNotOpenDatabaseScope() {
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/auth/session")).isFalse();
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/api/v1/health")).isFalse();
		assertThat(DatabaseOwnerScopeFilter.requiresDatabaseScope("/other/path")).isFalse();
	}
}
