package com.bookflow;

import com.bookflow.support.PostgresTestcontainerConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class FlywayMigrationIT {

    private static final List<String> FORBIDDEN_BUSINESS_TABLES = List.of(
            "branches",
            "employees",
            "services",
            "schedules",
            "bookings",
            "payments"
    );

    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Flyway flyway;

    @Test
    void springBootMigratesAnIsolatedPostgresqlTestcontainer() throws SQLException {
        assertTestcontainerConnection();

        BaselineHistory baseline = readBaselineHistory();
        assertThat(baseline.version()).isEqualTo("1");
        assertThat(baseline.description()).isEqualTo("baseline");
        assertThat(baseline.checksum()).isNotNull();
        assertThat(baseline.success()).isTrue();
        assertThat(countBaselineRows()).isEqualTo(1);

        MigrationInfo versionOne = findVersionOneMigration();
        assertThat(versionOne.getChecksum()).isEqualTo(baseline.checksum());
        MigrationInfo versionTwo = findMigration("2");
        assertThat(versionTwo.getDescription()).isEqualTo("authentication schema");
        MigrationInfo versionThree = findMigration("3");
        assertThat(versionThree.getDescription()).isEqualTo("password reset tokens");
        MigrationInfo versionFour = findMigration("4");
        assertThat(versionFour.getDescription()).isEqualTo("business and membership schema");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();

        MigrateResult repeatedMigration = flyway.migrate();
        assertThat(repeatedMigration.success).isTrue();
        assertThat(repeatedMigration.migrationsExecuted).isZero();
        assertThat(countBaselineRows()).isEqualTo(1);
        assertThat(countMigrationRows("2")).isEqualTo(1);
        assertThat(countMigrationRows("3")).isEqualTo(1);
        assertThat(countMigrationRows("4")).isEqualTo(1);
        assertExpectedTablesExist();
    }

    private void assertTestcontainerConnection() throws SQLException {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDockerImageName()).isEqualTo("postgres:17.10-alpine");
        assertThat(postgresContainer.getDatabaseName()).isEqualTo("bookflow_test");

        int mappedPort = postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);
        assertThat(mappedPort).isPositive();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertThat(metadata.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(metadata.getDatabaseMajorVersion()).isEqualTo(17);
            assertThat(connection.getCatalog()).isEqualTo("bookflow_test");
            assertThat(metadata.getURL()).isEqualTo(postgresContainer.getJdbcUrl());
            assertThat(metadata.getURL()).contains(":" + mappedPort + "/bookflow_test");
            assertThat(metadata.getURL()).doesNotContain(":5433/bookflow");
        }
    }

    private BaselineHistory readBaselineHistory() throws SQLException {
        String sql = "SELECT version, description, checksum, success "
                + "FROM public.flyway_schema_history WHERE version = '1'";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            assertThat(resultSet.next()).isTrue();
            BaselineHistory baseline = new BaselineHistory(
                    resultSet.getString("version"),
                    resultSet.getString("description"),
                    resultSet.getObject("checksum", Integer.class),
                    resultSet.getBoolean("success")
            );
            assertThat(resultSet.next()).isFalse();
            return baseline;
        }
    }

    private int countBaselineRows() throws SQLException {
        return countMigrationRows("1");
    }

    private int countMigrationRows(String version) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM public.flyway_schema_history WHERE version = '" + version + "'")) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private MigrationInfo findVersionOneMigration() {
        return findMigration("1");
    }

    private MigrationInfo findMigration(String version) {
        return List.of(flyway.info().all()).stream()
                .filter(info -> MigrationVersion.fromVersion(version).equals(info.getVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Flyway version " + version + " was not found"));
    }

    private void assertExpectedTablesExist() throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
        }

        assertThat(tables).containsExactly(
                "auth_sessions",
                "business_memberships",
                "businesses",
                "flyway_schema_history",
                "password_reset_tokens",
                "refresh_tokens",
                "users"
        );
        assertThat(tables).doesNotContainAnyElementsOf(FORBIDDEN_BUSINESS_TABLES);
    }

    private record BaselineHistory(String version, String description, Integer checksum, boolean success) {
    }
}
