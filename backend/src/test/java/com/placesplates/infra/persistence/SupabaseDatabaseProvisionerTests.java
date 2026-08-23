package com.placesplates.infra.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}
