package model.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Factory for obtaining SQLite database connections.
 * Supports both file-based and in-memory databases.
 */
public class ConnectionFactory {

    private final String jdbcUrl;

    /**
     * Creates a factory for a file-based database at the given path.
     *
     * @param dbPath absolute path to the SQLite database file
     */
    public ConnectionFactory(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    /**
     * Creates a factory for an in-memory database (useful for testing).
     * Uses a shared cache so multiple connections see the same data.
     */
    public static ConnectionFactory forMemory() {
        return new ConnectionFactory("file::memory:?cache=shared");
    }

    /**
     * Obtains a new JDBC connection to the database.
     *
     * @return open Connection
     * @throws ConnectionException if the driver cannot connect
     */
    public Connection getConnection() throws ConnectionException {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new ConnectionException("Failed to obtain database connection: " + jdbcUrl, e);
        }
    }
}
