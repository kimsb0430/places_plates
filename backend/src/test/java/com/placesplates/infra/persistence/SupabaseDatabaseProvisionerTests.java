package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;

import org.junit.jupiter.api.Test;

class SupabaseDatabaseProvisionerTests {

	@Test
	void sqlLiteralEscapesSingleQuotesWithoutLoggingOrEncodingThePassword() {
		assertThat(SupabaseDatabaseProvisioner.quoteSqlLiteral("safe'password"))
			.isEqualTo("'safe''password'");
	}

	@Test
	void sqlLiteralRejectsControlCharacters() {
		assertThatThrownBy(() -> SupabaseDatabaseProvisioner.quoteSqlLiteral("unsafe\npassword"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void onlyPasswordAuthenticationFailuresAreEligibleForPoolerRetry() {
		assertThat(SupabaseDatabaseProvisioner.isPasswordAuthenticationFailure(
			new SQLException("authentication failed", "28P01")
		)).isTrue();
		assertThat(SupabaseDatabaseProvisioner.isPasswordAuthenticationFailure(
			new SQLException("connection failed", "08001")
		)).isFalse();
	}
}
