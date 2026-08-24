package com.placesplates.infra.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class SupabaseDatabaseProvisioner {

	private static final String RUNTIME_ROLE = "placesplates_app";
	private static final int EXPECTED_MIGRATION_COUNT = 10;
	private static final int EXPECTED_FORCED_RLS_TABLE_COUNT = 13;

	private SupabaseDatabaseProvisioner() {
	}

	public static void main(String[] args) throws SQLException {
		ProvisioningConfiguration configuration = ProvisioningConfiguration.from(System.getenv());

		prepareRuntimeRole(configuration);
		MigrateResult migrationResult = migrateDatabase(configuration);
		verifyAdminState(configuration);
		verifyRuntimeState(configuration);

		System.out.printf(
			"PASS: Supabase database provisioned; %d migration(s) applied in this run.%n",
			migrationResult.migrationsExecuted
		);
	}

	private static void prepareRuntimeRole(ProvisioningConfiguration configuration) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			configuration.databaseUrl(),
			configuration.adminUsername(),
			configuration.adminPassword()
		)) {
			if (!roleExists(connection, RUNTIME_ROLE)) {
				try (Statement statement = connection.createStatement()) {
					statement.execute(
						"CREATE ROLE placesplates_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE "
							+ "NOINHERIT NOREPLICATION NOBYPASSRLS"
					);
				}
			}

			assertRestrictedRuntimeRole(connection);
			try (Statement statement = connection.createStatement()) {
				statement.execute(
					"ALTER ROLE placesplates_app WITH LOGIN PASSWORD "
						+ quoteSqlLiteral(configuration.runtimePassword())
				);
			}
		}
	}

	private static MigrateResult migrateDatabase(ProvisioningConfiguration configuration) {
		Flyway flyway = Flyway.configure()
			.dataSource(
				configuration.databaseUrl(),
				configuration.adminUsername(),
				configuration.adminPassword()
			)
			.locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
			.load();
		return flyway.migrate();
	}

	private static void verifyAdminState(ProvisioningConfiguration configuration) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			configuration.databaseUrl(),
			configuration.adminUsername(),
			configuration.adminPassword()
		)) {
			assertCount(
				connection,
				"SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE",
				EXPECTED_MIGRATION_COUNT,
				"Flyway migration history"
			);
			assertCount(
				connection,
				"""
				SELECT COUNT(*)
				FROM pg_class table_metadata
				JOIN pg_namespace table_schema ON table_schema.oid = table_metadata.relnamespace
				WHERE table_schema.nspname = 'public'
				  AND table_metadata.relname IN (
				      'profiles', 'trips', 'places', 'posts', 'restaurant_details',
				      'destination_details', 'tags', 'post_tags', 'photos', 'photo_assets',
				      'upload_batches', 'upload_items', 'image_processing_jobs'
				  )
				  AND table_metadata.relrowsecurity = TRUE
				  AND table_metadata.relforcerowsecurity = TRUE
				""",
				EXPECTED_FORCED_RLS_TABLE_COUNT,
				"forced RLS tables"
			);
			assertCount(
				connection,
				"SELECT COUNT(*) FROM pg_extension WHERE extname = 'postgis'",
				1,
				"PostGIS extensions"
			);
			assertNoSupabaseDataApiPrivilege(connection, "anon");
			assertNoSupabaseDataApiPrivilege(connection, "authenticated");
			assertRuntimeSessionTablePrivileges(connection);
		}
	}

	private static void verifyRuntimeState(ProvisioningConfiguration configuration) throws SQLException {
		try (Connection connection = DriverManager.getConnection(
			configuration.databaseUrl(),
			configuration.runtimeUsername(),
			configuration.runtimePassword()
		)) {
			try (Statement statement = connection.createStatement();
				 ResultSet resultSet = statement.executeQuery(
					 "SELECT current_user, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = current_user"
				 )) {
				if (!resultSet.next()
					|| !RUNTIME_ROLE.equals(resultSet.getString("current_user"))
					|| resultSet.getBoolean("rolsuper")
					|| resultSet.getBoolean("rolbypassrls")) {
					throw new IllegalStateException("Runtime database role can bypass row security");
				}
			}

			assertCount(connection, "SELECT COUNT(*) FROM posts", 0, "rows visible without request scope");
			assertQuerySucceeds(connection, "SELECT COUNT(*) FROM spring_session");
		}
	}

	private static void assertRuntimeSessionTablePrivileges(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"""
			SELECT has_table_privilege('placesplates_app', 'public.spring_session', 'SELECT')
			   AND has_table_privilege('placesplates_app', 'public.spring_session', 'INSERT')
			   AND has_table_privilege('placesplates_app', 'public.spring_session', 'UPDATE')
			   AND has_table_privilege('placesplates_app', 'public.spring_session', 'DELETE')
			   AND has_table_privilege('placesplates_app', 'public.spring_session_attributes', 'SELECT')
			   AND has_table_privilege('placesplates_app', 'public.spring_session_attributes', 'INSERT')
			   AND has_table_privilege('placesplates_app', 'public.spring_session_attributes', 'UPDATE')
			   AND has_table_privilege('placesplates_app', 'public.spring_session_attributes', 'DELETE')
			"""
		); ResultSet resultSet = statement.executeQuery()) {
			if (!resultSet.next() || !resultSet.getBoolean(1)) {
				throw new IllegalStateException("Runtime database role cannot manage JDBC sessions");
			}
		}
	}

	private static void assertQuerySucceeds(Connection connection, String query) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet ignored = statement.executeQuery(query)) {
			// 実行ロールがセッションテーブルを参照できることだけを検証する。
		}
	}

	private static boolean roleExists(Connection connection, String roleName) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)"
		)) {
			statement.setString(1, roleName);
			try (ResultSet resultSet = statement.executeQuery()) {
				return resultSet.next() && resultSet.getBoolean(1);
			}
		}
	}

	private static void assertRestrictedRuntimeRole(Connection connection) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(
			"""
			SELECT NOT rolsuper
			   AND NOT rolcreaterole
			   AND NOT rolcreatedb
			   AND NOT rolreplication
			   AND NOT rolbypassrls
			FROM pg_roles
			WHERE rolname = ?
			"""
		)) {
			statement.setString(1, RUNTIME_ROLE);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next() || !resultSet.getBoolean(1)) {
					throw new IllegalStateException("Existing runtime database role has elevated privileges");
				}
			}
		}
	}

	private static void assertNoSupabaseDataApiPrivilege(Connection connection, String roleName)
		throws SQLException {
		if (!roleExists(connection, roleName)) {
			return;
		}

		try (PreparedStatement statement = connection.prepareStatement(
			"""
			SELECT has_table_privilege(?, 'public.app_users', 'SELECT')
			    OR has_table_privilege(?, 'public.spring_session', 'SELECT')
			"""
		)) {
			statement.setString(1, roleName);
			statement.setString(2, roleName);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next() || resultSet.getBoolean(1)) {
					throw new IllegalStateException(roleName + " can read backend-owned application tables");
				}
			}
		}
	}

	private static void assertCount(
		Connection connection,
		String query,
		int expectedCount,
		String description
	) throws SQLException {
		try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(query)) {
			if (!resultSet.next() || resultSet.getInt(1) != expectedCount) {
				throw new IllegalStateException("Unexpected " + description);
			}
		}
	}

	static String quoteSqlLiteral(String value) {
		if (value.indexOf('\0') >= 0 || value.contains("\r") || value.contains("\n")) {
			throw new IllegalArgumentException("Database password contains an unsupported control character");
		}
		return "'" + value.replace("'", "''") + "'";
	}

	private record ProvisioningConfiguration(
		String databaseUrl,
		String adminUsername,
		String adminPassword,
		String runtimeUsername,
		String runtimePassword
	) {

		private static ProvisioningConfiguration from(Map<String, String> environment) {
			String databaseUrl = require(environment, "SUPABASE_DATABASE_URL");
			if (!databaseUrl.startsWith("jdbc:postgresql://") || !databaseUrl.contains("sslmode=")) {
				throw new IllegalArgumentException("SUPABASE_DATABASE_URL must be a PostgreSQL JDBC SSL URL");
			}

			String runtimePassword = require(environment, "SUPABASE_RUNTIME_DATABASE_PASSWORD");
			if (runtimePassword.length() < 20) {
				throw new IllegalArgumentException("Runtime database password must contain at least 20 characters");
			}

			return new ProvisioningConfiguration(
				databaseUrl,
				require(environment, "SUPABASE_ADMIN_DATABASE_USERNAME"),
				require(environment, "SUPABASE_ADMIN_DATABASE_PASSWORD"),
				require(environment, "SUPABASE_RUNTIME_DATABASE_USERNAME"),
				runtimePassword
			);
		}

		private static String require(Map<String, String> environment, String name) {
			String value = environment.get(name);
			if (value == null || value.isBlank()) {
				throw new IllegalArgumentException(name + " is required");
			}
			return value;
		}
	}
}
