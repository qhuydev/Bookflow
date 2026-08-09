package com.bookflow;

import com.bookflow.support.PostgresTestcontainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("testcontainers")
@Import(PostgresTestcontainerConfiguration.class)
class BusinessMembershipSchemaMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void storesBusinessesAndSupportsManyToManyMemberships() throws SQLException {
        UUID firstBusiness = insertBusiness("first");
        UUID secondBusiness = insertBusiness("second");
        UUID firstUser = insertUser("first-member");
        UUID secondUser = insertUser("second-member");

        insertMembership(firstBusiness, firstUser, "OWNER", "ACTIVE");
        insertMembership(secondBusiness, firstUser, "ADMIN", "ACTIVE");
        insertMembership(firstBusiness, secondUser, "STAFF", "SUSPENDED");

        assertThat(countMembershipsForUser(firstUser)).isEqualTo(2);
        assertThat(countMembershipsForTenant(firstBusiness)).isEqualTo(2);
        assertThatThrownBy(() -> insertMembership(firstBusiness, firstUser, "STAFF", "ACTIVE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void databaseRejectsInvalidBusinessAndMembershipInvariants() throws SQLException {
        String existingSlug = slug("constraints");
        UUID business = insertBusinessWithValues(
                "Business constraints", existingSlug, "SALON", "Asia/Ho_Chi_Minh", "ACTIVE"
        );
        UUID user = insertUser("constraints-member");

        assertThatThrownBy(() -> insertBusinessWithValues("Duplicate", existingSlug, "SALON", "Asia/Ho_Chi_Minh", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertBusinessWithValues("Invalid type", slug("invalid-type"), "RESTAURANT", "Asia/Ho_Chi_Minh", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertBusinessWithValues("Invalid zone", slug("invalid-zone"), "SALON", "not a timezone", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertMembership(business, user, "SYSTEM_ADMIN", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertMembership(business, user, "STAFF", "PENDING"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertMembership(UUID.randomUUID(), user, "STAFF", "ACTIVE"))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertMembership(business, UUID.randomUUID(), "STAFF", "ACTIVE"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void restrictsBusinessAndUserDeletionAndExposesMembershipIndexes() throws SQLException {
        UUID business = insertBusiness("delete-restrict");
        UUID user = insertUser("delete-restrict-member");
        insertMembership(business, user, "OWNER", "ACTIVE");

        assertThatThrownBy(() -> delete("DELETE FROM businesses WHERE id=?", business))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> delete("DELETE FROM users WHERE id=?", user))
                .isInstanceOf(SQLException.class);
        assertThat(membershipIndexes()).contains(
                "business_memberships_user_tenant_key",
                "idx_business_memberships_tenant_status_role",
                "idx_business_memberships_user_status"
        );
    }

    private UUID insertBusiness(String suffix) throws SQLException {
        return insertBusinessWithValues(
                "Business " + suffix,
                slug(suffix),
                "SALON",
                "Asia/Ho_Chi_Minh",
                "ACTIVE"
        );
    }

    private UUID insertBusinessWithValues(
            String name,
            String slug,
            String type,
            String timeZone,
            String status
    ) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO businesses (id, name, slug, business_type, time_zone, status)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, name);
            statement.setString(3, slug);
            statement.setString(4, type);
            statement.setString(5, timeZone);
            statement.setString(6, status);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID insertUser(String suffix) throws SQLException {
        UUID id = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO users (id, normalized_email, password_hash)
                     VALUES (?, ?, ?)
                     """)) {
            statement.setObject(1, id);
            statement.setString(2, suffix + "-" + id + "@example.test");
            statement.setString(3, "$argon2id$schema-test-only");
            statement.executeUpdate();
        }
        return id;
    }

    private void insertMembership(UUID tenantId, UUID userId, String role, String status) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO business_memberships (id, tenant_id, user_id, role, status)
                     VALUES (?, ?, ?, ?, ?)
                     """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, tenantId);
            statement.setObject(3, userId);
            statement.setString(4, role);
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private int countMembershipsForUser(UUID userId) throws SQLException {
        return count("SELECT COUNT(*) FROM business_memberships WHERE user_id=?", userId);
    }

    private int countMembershipsForTenant(UUID tenantId) throws SQLException {
        return count("SELECT COUNT(*) FROM business_memberships WHERE tenant_id=?", tenantId);
    }

    private int count(String sql, UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt(1);
            }
        }
    }

    private void delete(String sql, UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private List<String> membershipIndexes() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT indexname
                     FROM pg_indexes
                     WHERE schemaname='public' AND tablename='business_memberships'
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            List<String> indexes = new ArrayList<>();
            while (resultSet.next()) {
                indexes.add(resultSet.getString(1));
            }
            return indexes;
        }
    }

    private String slug(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
