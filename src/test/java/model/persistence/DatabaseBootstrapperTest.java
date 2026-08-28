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

        assertEquals(3, version, "schema version should be 3 after bootstrap (V1 + V2 + V3)");
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

        assertEquals(3, first);
        assertEquals(3, second, "re-bootstrapping should not change version");
    }

    @Test
    void shouldResolveFileDbPath(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(3, version);
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

        assertEquals(3, version, "schema version should be 2 then 3 after V2+V3 migration");
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

    // =========================================================================
    // V3 Migration tests
    // =========================================================================

    @Test
    void shouldReachVersion3AfterBootstrap() throws Exception {
        ConnectionFactory factory = ConnectionFactory.forMemory();
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(3, version, "schema version should be 3 after V3 migration");
    }

    @Test
    void shouldDeleteStaleNullMachineIdRowsAfterV3(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("v3_cleanup.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        // Bootstrap: creates full schema at version 3
        bootstrapper.bootstrap();

        // Insert a stale V1-style row with NULL machine_id
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO game_state (game_id, pot, machine_id, human_chips) VALUES ('stale_latest', 0, NULL, 0)");
        }

        // Verify the stale row was inserted
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM game_state WHERE machine_id IS NULL")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1), "stale row should exist before V3 cleanup");
        }

        // Simulate V2 state: downgrade schema version so V3 can re-run
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM schema_version WHERE version = 3");
        }

        // Re-bootstrap: detects version 2, applies V3 migration which deletes stale rows
        bootstrapper.bootstrap();

        // Stale rows should be gone
        try (Connection conn = factory.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM game_state WHERE machine_id IS NULL")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "V3 should delete rows with NULL machine_id");
        }
    }
}
