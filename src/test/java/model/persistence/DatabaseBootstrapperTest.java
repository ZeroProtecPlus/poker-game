package model.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DatabaseBootstrapper.
 */
class DatabaseBootstrapperTest {

    @BeforeAll
    static void loadSqliteDriver() throws Exception {
        Class.forName("org.sqlite.JDBC");
    }

    @Test
    void shouldCreateSchemaVersionTableOnBootstrap() throws Exception {
        ConnectionFactory factory = ConnectionFactory.forMemory();
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(2, version, "schema version should be 2 after bootstrap (V1 + V2)");
    }

    @Test
    void shouldCreatePlayerTableOnBootstrap(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("tables.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='table' AND name='players'")) {
            assertTrue(rs.next(), "players table should exist");
        }
    }

    @Test
    void shouldBeIdempotentOnMultipleBootstraps() throws Exception {
        ConnectionFactory factory = ConnectionFactory.forMemory();
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int first = bootstrapper.bootstrap();
        int second = bootstrapper.bootstrap();

        assertEquals(2, first);
        assertEquals(2, second, "re-bootstrapping should not change version");
    }

    @Test
    void shouldResolveFileDbPath(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(2, version);
        assertTrue(java.nio.file.Files.exists(dbFile), "database file should be created");
    }

    // =========================================================================
    // V2 Migration tests
    // =========================================================================

    @Test
    void shouldApplyV2MigrationAndReachVersion2() throws Exception {
        ConnectionFactory factory = ConnectionFactory.forMemory();
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(2, version, "schema version should be 2 after V2 migration");
    }

    @Test
    void shouldHaveMachineIdColumnAfterV2(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("v2_machine_id.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(game_state)")) {
            boolean found = false;
            while (rs.next()) {
                if ("machine_id".equals(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "game_state should have machine_id column after V2");
        }
    }

    @Test
    void shouldHaveHumanChipsColumnAfterV2(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("v2_human_chips.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(game_state)")) {
            boolean found = false;
            while (rs.next()) {
                if ("human_chips".equals(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "game_state should have human_chips column after V2");
        }
    }

    @Test
    void shouldHaveAiColumnInGamePlayersAfterV2(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("v2_ai.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(game_players)")) {
            boolean found = false;
            while (rs.next()) {
                if ("ai".equals(rs.getString("name"))) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "game_players should have ai column after V2");
        }
    }

    @Test
    void shouldPreserveExistingDataAfterV2Migration(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("migration.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        // Insert test data
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO players (player_id, name, chips) VALUES ('test-1', 'TestPlayer', 5000)");
            stmt.execute("INSERT INTO game_state (game_id, pot) VALUES ('test_latest', 100)");
        }

        // Data should still exist after migration (V2 already applied)
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM players WHERE name='TestPlayer'")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "existing player data should be preserved");
        }
    }

    @Test
    void shouldHaveMachineIdUniqueIndexAfterV2(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("v2_index.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();

        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_game_state_machine_id'")) {
            assertTrue(rs.next(), "idx_game_state_machine_id index should exist after V2");
        }
    }
}
