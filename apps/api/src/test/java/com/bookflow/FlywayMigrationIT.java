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
            "schedules",
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

        MigrationInfo versionOne = findMigration("1");
        assertThat(versionOne.getChecksum()).isEqualTo(baseline.checksum());

        MigrationInfo versionTwo = findMigration("2");
        assertThat(versionTwo.getDescription())
                .isEqualTo("authentication schema");

        MigrationInfo versionThree = findMigration("3");
        assertThat(versionThree.getDescription())
                .isEqualTo("password reset tokens");

        MigrationInfo versionFour = findMigration("4");
        assertThat(versionFour.getDescription())
                .isEqualTo("business and membership schema");

        MigrationInfo versionFive = findMigration("5");
        assertThat(versionFive.getDescription())
                .isEqualTo("business configuration");

        MigrationInfo versionSix = findMigration("6");
        assertThat(versionSix.getDescription())
                .isEqualTo("branch schema");

        MigrationInfo versionSeven = findMigration("7");
        assertThat(versionSeven.getDescription())
                .isEqualTo("employee schema");

        MigrationInfo versionEight = findMigration("8");
        assertThat(versionEight.getDescription())
                .isEqualTo("business member employee link");

        MigrationInfo versionNine = findMigration("9");
        assertThat(versionNine.getDescription())
                .isEqualTo("service catalog");

        MigrationInfo versionTen = findMigration("10");
        assertThat(versionTen.getDescription())
                .isEqualTo("schedule management");

        MigrationInfo versionEleven = findMigration("11");
        assertThat(versionEleven.getDescription())
                .isEqualTo("booking foundation");

        MigrationInfo versionTwelve = findMigration("12");
        assertThat(versionTwelve.getDescription())
                .isEqualTo("booking concurrency and idempotency");

        MigrationInfo versionThirteen = findMigration("13");
        assertThat(versionThirteen.getDescription())
                .isEqualTo("booking lifecycle");

        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.info().pending()).isEmpty();

        MigrateResult repeatedMigration = flyway.migrate();

        assertThat(repeatedMigration.success).isTrue();
        assertThat(repeatedMigration.migrationsExecuted).isZero();

        assertThat(countMigrationRows("1")).isEqualTo(1);
        assertThat(countMigrationRows("2")).isEqualTo(1);
        assertThat(countMigrationRows("3")).isEqualTo(1);
        assertThat(countMigrationRows("4")).isEqualTo(1);
        assertThat(countMigrationRows("5")).isEqualTo(1);
        assertThat(countMigrationRows("6")).isEqualTo(1);
        assertThat(countMigrationRows("7")).isEqualTo(1);
        assertThat(countMigrationRows("8")).isEqualTo(1);
        assertThat(countMigrationRows("9")).isEqualTo(1);
        assertThat(countMigrationRows("10")).isEqualTo(1);
        assertThat(countMigrationRows("11")).isEqualTo(1);
        assertThat(countMigrationRows("12")).isEqualTo(1);
        assertThat(countMigrationRows("13")).isEqualTo(1);

        assertExpectedTablesExist();
    }

    private void assertTestcontainerConnection() throws SQLException {
        assertThat(postgresContainer.isRunning()).isTrue();
        assertThat(postgresContainer.getDockerImageName())
                .isEqualTo("postgres:17.10-alpine");
        assertThat(postgresContainer.getDatabaseName())
                .isEqualTo("bookflow_test");

        int mappedPort =
                postgresContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT);

        assertThat(mappedPort).isPositive();

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();

            assertThat(metadata.getDatabaseProductName())
                    .isEqualTo("PostgreSQL");
            assertThat(metadata.getDatabaseMajorVersion())
                    .isEqualTo(17);
            assertThat(connection.getCatalog())
                    .isEqualTo("bookflow_test");
            assertThat(metadata.getURL())
                    .isEqualTo(postgresContainer.getJdbcUrl());
            assertThat(metadata.getURL())
                    .contains(":" + mappedPort + "/bookflow_test");
            assertThat(metadata.getURL())
                    .doesNotContain(":5433/bookflow");
        }
    }

    private BaselineHistory readBaselineHistory() throws SQLException {
        String sql = """
                SELECT version, description, checksum, success
                FROM public.flyway_schema_history
                WHERE version = '1'
                """;

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
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
        String sql = """
                SELECT COUNT(*)
                FROM public.flyway_schema_history
                WHERE version = '%s'
                """.formatted(version);

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            assertThat(resultSet.next()).isTrue();
            return resultSet.getInt(1);
        }
    }

    private MigrationInfo findMigration(String version) {
        MigrationVersion migrationVersion =
                MigrationVersion.fromVersion(version);

        return List.of(flyway.info().all()).stream()
                .filter(info -> migrationVersion.equals(info.getVersion()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Flyway version " + version + " was not found"
                ));
    }

    private void assertExpectedTablesExist() throws SQLException {
        List<String> tables = new ArrayList<>();

        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;

        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql)
        ) {
            while (resultSet.next()) {
                tables.add(resultSet.getString("table_name"));
            }
        }

        assertThat(tables).containsExactly(
                "auth_sessions",
                "booking_idempotency_keys",
                "booking_items",
                "booking_reschedule_history",
                "booking_status_history",
                "bookings",
                "branch_services",
                "branches",
                "business_memberships",
                "businesses",
                "employee_branch_assignments",
                "employee_services",
                "employees",
                "flyway_schema_history",
                "password_reset_tokens",
                "refresh_tokens",
                "schedule_breaks",
                "schedule_exceptions",
                "services",
                "users",
                "working_schedule_rules"
        );

        assertThat(tables)
                .doesNotContainAnyElementsOf(FORBIDDEN_BUSINESS_TABLES);
    }

    private record BaselineHistory(
            String version,
            String description,
            Integer checksum,
            boolean success
    ) {
    }
}
