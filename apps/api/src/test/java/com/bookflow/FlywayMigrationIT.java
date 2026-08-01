package com.bookflow;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationIT {

    private static final Pattern TEMPORARY_SCHEMA_PATTERN =
            Pattern.compile("^bf006_it_[a-f0-9]{32}$");

    @Test
    void migratesAndValidatesInAnIsolatedPostgresqlSchema() throws SQLException {
        String url = requireEnvironment("BOOKFLOW_TEST_DB_URL");
        String username = requireEnvironment("BOOKFLOW_TEST_DB_USERNAME");
        String password = requireEnvironment("BOOKFLOW_TEST_DB_PASSWORD");
        String schema = "bf006_it_" + UUID.randomUUID().toString().replace("-", "");
        String quotedSchema = quoteTemporarySchema(schema);
        boolean schemaCreated = false;

        try {
            execute(url, username, password, "CREATE SCHEMA " + quotedSchema);
            schemaCreated = true;

            Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .defaultSchema(schema)
                    .schemas(schema)
                    .baselineOnMigrate(false)
                    .validateOnMigrate(true)
                    .outOfOrder(false)
                    .cleanDisabled(true)
                    .load();

            MigrateResult firstMigration = flyway.migrate();
            assertThat(firstMigration.success).isTrue();
            assertThat(firstMigration.migrationsExecuted).isEqualTo(1);
            assertBaselineHistory(url, username, password, quotedSchema);

            flyway.validate();

            MigrateResult secondMigration = flyway.migrate();
            assertThat(secondMigration.success).isTrue();
            assertThat(secondMigration.migrationsExecuted).isZero();
            assertThat(countBaselineRows(url, username, password, quotedSchema)).isEqualTo(1);
        } finally {
            if (schemaCreated) {
                execute(url, username, password, "DROP SCHEMA " + quotedSchema + " CASCADE");
            }
        }
    }

    private static void assertBaselineHistory(
            String url,
            String username,
            String password,
            String quotedSchema
    ) throws SQLException {
        String sql = "SELECT version, description, checksum, success FROM "
                + quotedSchema
                + ".flyway_schema_history WHERE version = '1'";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getString("version")).isEqualTo("1");
            assertThat(resultSet.getString("description")).isEqualTo("baseline");
            assertThat(resultSet.getObject("checksum")).isNotNull();
            assertThat(resultSet.getBoolean("success")).isTrue();
            assertThat(resultSet.next()).isFalse();
        }
    }

    private static int countBaselineRows(
            String url,
            String username,
            String password,
            String quotedSchema
    ) throws SQLException {
        String sql = "SELECT COUNT(*) FROM "
                + quotedSchema
                + ".flyway_schema_history WHERE version = '1'";

        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private static void execute(String url, String username, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static String quoteTemporarySchema(String schema) {
        if (!TEMPORARY_SCHEMA_PATTERN.matcher(schema).matches()) {
            throw new IllegalArgumentException("Temporary schema name is not safe for BF-006");
        }
        return '"' + schema + '"';
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required when the flyway-it profile is enabled");
        }
        return value;
    }
}
