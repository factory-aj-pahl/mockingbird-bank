package com.mockingbirdbank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the real Flyway migrations against a throwaway Postgres: data written under the V1 schema
 * must survive V2 unchanged and unenrolled, and V2's constraints must reject what the application
 * code never does, such as linking an account to another customer's program.
 */
@Testcontainers
class ProgramMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final String CHECK_VIOLATION = "23514";

    @Test
    void existingAccountsSurviveTheMigrationAsUsdWithoutAProgram() throws SQLException {
        freshSchemaAt("1");
        try (Connection connection = connect()) {
            long holder =
                    insert(
                            connection,
                            "INSERT INTO account_holder (full_name, email)"
                                    + " VALUES ('Existing Customer', 'existing@example.com')"
                                    + " RETURNING id");
            insert(
                    connection,
                    "INSERT INTO account (holder_id, account_number, account_type, nickname,"
                            + " balance, opened_at) VALUES (?, '5550001', 'SAVINGS', 'Old Savings',"
                            + " 250.00, DATE '2020-01-01') RETURNING id",
                    holder);
        }

        migrate();

        try (Connection connection = connect();
                PreparedStatement query =
                        connection.prepareStatement(
                                "SELECT a.balance, a.currency, a.program_participation_id,"
                                        + " h.date_of_birth, (SELECT count(*) FROM"
                                        + " program_participation) AS programs FROM account a"
                                        + " JOIN account_holder h ON h.id = a.holder_id"
                                        + " WHERE a.account_number = '5550001'");
                ResultSet row = query.executeQuery()) {
            assertThat(row.next()).isTrue();
            assertThat(row.getBigDecimal("balance")).isEqualByComparingTo("250.00");
            assertThat(row.getString("currency")).isEqualTo("USD");
            assertThat(row.getObject("program_participation_id")).isNull();
            assertThat(row.getObject("date_of_birth")).isNull();
            assertThat(row.getLong("programs")).isZero();
        }
    }

    @Test
    void databaseRejectsLinkingAnAccountToAnotherCustomersProgram() throws SQLException {
        freshSchemaAt("latest");
        try (Connection connection = connect()) {
            long owner = insertHolder(connection, "owner@example.com");
            long other = insertHolder(connection, "other@example.com");
            long othersAccount = insertSavings(connection, other, "5550002");
            long ownersProgram = insertProgram(connection, owner, "REGULAR");
            long othersProgram = insertProgram(connection, other, "YOUTH_SAVINGS");

            assertThatThrownBy(() -> link(connection, othersAccount, ownersProgram))
                    .isInstanceOfSatisfying(
                            SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo(FOREIGN_KEY_VIOLATION));
            assertThat(link(connection, othersAccount, othersProgram)).isEqualTo(1);
        }
    }

    @Test
    void databaseRejectsUnknownProgramTypesAndCurrencyCodes() throws SQLException {
        freshSchemaAt("latest");
        try (Connection connection = connect()) {
            long holder = insertHolder(connection, "holder@example.com");
            long account = insertSavings(connection, holder, "5550003");

            assertThatThrownBy(() -> insertProgram(connection, holder, "PLATINUM"))
                    .isInstanceOfSatisfying(
                            SQLException.class,
                            e -> assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION));
            try (PreparedStatement update =
                    connection.prepareStatement(
                            "UPDATE account SET currency = 'usd' WHERE id = ?")) {
                update.setLong(1, account);
                assertThatThrownBy(update::executeUpdate)
                        .isInstanceOfSatisfying(
                                SQLException.class,
                                e -> assertThat(e.getSQLState()).isEqualTo(CHECK_VIOLATION));
            }
        }
    }

    private static void freshSchemaAt(String target) {
        Flyway flyway = flyway(target);
        flyway.clean();
        flyway.migrate();
    }

    private static void migrate() {
        flyway("latest").migrate();
    }

    private static Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static long insertHolder(Connection connection, String email) throws SQLException {
        return insert(
                connection,
                "INSERT INTO account_holder (full_name, email) VALUES ('Customer', ?) RETURNING id",
                email);
    }

    private static long insertSavings(Connection connection, long holder, String number)
            throws SQLException {
        return insert(
                connection,
                "INSERT INTO account (holder_id, account_number, account_type, balance, opened_at)"
                        + " VALUES (?, ?, 'SAVINGS', 500.00, DATE '2024-01-02') RETURNING id",
                holder,
                number);
    }

    private static long insertProgram(Connection connection, long holder, String type)
            throws SQLException {
        return insert(
                connection,
                "INSERT INTO program_participation (holder_id, program_type) VALUES (?, ?)"
                        + " RETURNING id",
                holder,
                type);
    }

    private static int link(Connection connection, long account, long program) throws SQLException {
        try (PreparedStatement update =
                connection.prepareStatement(
                        "UPDATE account SET program_participation_id = ? WHERE id = ?")) {
            update.setLong(1, program);
            update.setLong(2, account);
            return update.executeUpdate();
        }
    }

    private static long insert(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet keys = statement.executeQuery()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }
}
