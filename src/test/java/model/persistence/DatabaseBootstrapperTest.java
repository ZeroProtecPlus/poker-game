package model.persistence;

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

    @Test
    void shouldCreateSchemaVersionTableOnBootstrap() throws Exception {
        ConnectionFactory factory = ConnectionFactory.forMemory();
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(1, version, "schema version should be 1 after initial bootstrap");
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

        assertEquals(1, first);
        assertEquals(1, second, "re-bootstrapping should not change version");
    }

    @Test
    void shouldResolveFileDbPath(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);

        int version = bootstrapper.bootstrap();

        assertEquals(1, version);
        assertTrue(java.nio.file.Files.exists(dbFile), "database file should be created");
    }
}
