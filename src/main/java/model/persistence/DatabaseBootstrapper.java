package model.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Bootstraps the SQLite database:
 * 1. Ensures the parent directory exists.
 * 2. Creates the connection.
 * 3. Runs pending schema migrations from classpath resources.
 * 4. Tracks schema version.
 */
public class DatabaseBootstrapper {

    private static final String DB_DIR = ".pokergame";
    private static final String DB_FILE = "poker.db";
    private static final String MIGRATION_PATH = "db/migration/";

    private final ConnectionFactory connectionFactory;

    /**
     * Bootstraps the default database at {@code user.home/.pokergame/poker.db}.
     */
    public DatabaseBootstrapper() {
        String home = System.getProperty("user.home");
        Path dbPath = Paths.get(home, DB_DIR, DB_FILE);
        ensureDirectory(dbPath.getParent());
        this.connectionFactory = new ConnectionFactory(dbPath.toString());
    }

    /**
     * Bootstraps using an explicit connection factory (useful for testing).
     */
    public DatabaseBootstrapper(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Runs all pending migrations and returns the current schema version.
     *
     * @return current schema version after bootstrap
     * @throws RepositoryException if migration fails
     */
    public int bootstrap() throws RepositoryException {
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            createSchemaVersionTable(conn);
            int currentVersion = getCurrentVersion(conn);
            List<Migration> pending = loadPendingMigrations(currentVersion);
            for (Migration migration : pending) {
                applyMigration(conn, migration);
            }
            conn.commit();
            return getCurrentVersion(conn);
        } catch (SQLException | ConnectionException | IOException e) {
            throw new RepositoryException("Database bootstrap failed", e);
        }
    }

    /**
     * Returns the connection factory used by this bootstrapper.
     */
    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    private void ensureDirectory(Path dir) {
        if (dir != null && !Files.exists(dir)) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot create database directory: " + dir, e);
            }
        }
    }

    private void createSchemaVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS schema_version (" +
                "  version INTEGER PRIMARY KEY," +
                "  applied_at TEXT NOT NULL DEFAULT (datetime('now'))" +
                ")"
            );
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            return rs.next() && rs.getObject(1) != null ? rs.getInt(1) : 0;
        }
    }

    private List<Migration> loadPendingMigrations(int currentVersion) throws IOException {
        List<Migration> migrations = new ArrayList<>();
        int version = 1;
        while (true) {
            String fileName = "V" + version + "__init.sql";
            String path = MIGRATION_PATH + fileName;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is == null) {
                    break;
                }
            }
            if (version > currentVersion) {
                migrations.add(new Migration(version, path));
            }
            version++;
        }
        return migrations;
    }

    private void applyMigration(Connection conn, Migration migration) throws SQLException, IOException {
        String sql = readResource(migration.resourcePath);
        String[] statements = sql.split(";\\s*");
        try (Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT OR REPLACE INTO schema_version (version) VALUES (" + migration.version + ")");
        }
    }

    private String readResource(String path) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Migration resource not found: " + path);
            }
            return new BufferedReader(new InputStreamReader(is))
                .lines().collect(Collectors.joining("\n"));
        }
    }

    private static final class Migration {
        final int version;
        final String resourcePath;

        Migration(int version, String resourcePath) {
            this.version = version;
            this.resourcePath = resourcePath;
        }
    }
}
