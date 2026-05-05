package model.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Fábrica de conexiones SQLite.
 * Centraliza la construcción del JDBC URL para que el resto del código
 * no tenga que conocer si la DB es archivo o memoria.
 */
public class ConnectionFactory {

    private final String jdbcUrl;

    /**
     * Crea una fábrica para una base de datos basada en archivo.
     *
     * @param dbPath ruta absoluta al archivo SQLite
     */
    public ConnectionFactory(String dbPath) {
        this.jdbcUrl = "jdbc:sqlite:" + dbPath;
    }

    /**
     * Crea una fábrica para base de datos en memoria (útil en tests).
     * Usa cache compartida para que múltiples conexiones vean los mismos datos.
     */
    public static ConnectionFactory forMemory() {
        return new ConnectionFactory("file::memory:?cache=shared");
    }

    /**
     * Obtiene una conexión JDBC nueva.
     *
     * @return conexión abierta
     * @throws ConnectionException si el driver no puede conectar
     */
    public Connection getConnection() throws ConnectionException {
        try {
            return DriverManager.getConnection(jdbcUrl);
        } catch (SQLException e) {
            throw new ConnectionException("Failed to obtain database connection: " + jdbcUrl, e);
        }
    }
}
