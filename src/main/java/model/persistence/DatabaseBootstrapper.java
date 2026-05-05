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
 * Bootstrap de la base SQLite:
 * 1) crea el directorio si no existe
 * 2) abre conexión
 * 3) corre migraciones pendientes desde resources
 * 4) registra versión aplicada
 */
public class DatabaseBootstrapper {

    private static final String DB_DIR = ".pokergame";
    private static final String DB_FILE = "poker.db";
    private static final String MIGRATION_PATH = "db/migration/";

    /**
     * Migraciones conocidas. El orden del array define el orden de ejecución.
     * Se aplica solo si version > currentVersion (evita re-ejecutar).
     */
    private static final String[] MIGRATION_FILES = {
        "V1__init.sql",
        "V2__add_machine_id.sql",
        "V3__cleanup_stale_machine_id.sql",
    };

    private final ConnectionFactory connectionFactory;

    /**
     * Inicializa la DB por defecto en {@code user.home/.pokergame/poker.db}.
     */
    public DatabaseBootstrapper() {
        String home = System.getProperty("user.home");
        Path dbPath = Paths.get(home, DB_DIR, DB_FILE);
        ensureDirectory(dbPath.getParent());
        this.connectionFactory = new ConnectionFactory(dbPath.toString());
    }

    /**
     * Inicializa usando una fábrica explícita (útil para testing).
     */
    public DatabaseBootstrapper(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Ejecuta migraciones pendientes y devuelve la versión actual.
     */
    public int bootstrap() throws RepositoryException {
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            createSchemaVersionTable(conn);
            int currentVersion = getCurrentVersion(conn);
            List<Migration> pending = loadPendingMigrations(currentVersion);
            // Aplica migraciones en orden y dentro de una transacción.
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
     * Devuelve la fábrica de conexiones utilizada por este bootstrapper.
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
        for (String fileName : MIGRATION_FILES) {
            int version = extractVersion(fileName);
            if (version > currentVersion) {
                // Solo carga migraciones nuevas, evita re-ejecutar versiones ya aplicadas.
                String path = MIGRATION_PATH + fileName;
                try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                    if (is != null) {
                        migrations.add(new Migration(version, path));
                    }
                }
            }
        }
        migrations.sort(Comparator.comparingInt(m -> m.version));
        return migrations;
    }

    /**
     * Extrae el número de versión desde un nombre tipo "V2__add_machine_id.sql".
     */
    private int extractVersion(String fileName) {
        int end = fileName.indexOf("__");
        if (end < 2) {
            throw new IllegalArgumentException("Invalid migration filename: " + fileName);
        }
        return Integer.parseInt(fileName.substring(1, end));
    }

    private void applyMigration(Connection conn, Migration migration) throws SQLException, IOException {
        // Split de statements para ejecutar scripts SQL multi-sentencia.
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
