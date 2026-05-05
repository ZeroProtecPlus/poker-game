package model.repository;

import model.persistence.ConnectionException;
import model.persistence.ConnectionFactory;
import model.persistence.RepositoryException;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Base de repositorio con helpers de conexión y transacciones.
 * Estandariza manejo de errores y atomicidad.
 */
public abstract class BaseRepository {

    protected final ConnectionFactory connectionFactory;

    protected BaseRepository(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Functional interface for actions that return a value using a Connection.
     */
    @FunctionalInterface
    protected interface ConnectionAction<T> {
        T apply(Connection conn) throws SQLException, RepositoryException;
    }

    /**
     * Functional interface for actions that use a Connection without returning a value.
     */
    @FunctionalInterface
    protected interface VoidConnectionAction {
        void accept(Connection conn) throws SQLException, RepositoryException;
    }

    /**
     * Executes an action with a connection and returns the result.
     */
    protected <T> T withConnection(ConnectionAction<T> action) throws RepositoryException {
        // Envoltorio único para traducir errores de JDBC a dominio de persistencia.
        try (Connection conn = connectionFactory.getConnection()) {
            return action.apply(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Database operation failed", e);
        } catch (ConnectionException e) {
            throw new RepositoryException("Failed to obtain connection", e);
        }
    }

    /**
     * Executes an action with a connection (no return value).
     */
    protected void doWithConnection(VoidConnectionAction action) throws RepositoryException {
        // Variante void para operaciones sin resultado explícito.
        try (Connection conn = connectionFactory.getConnection()) {
            action.accept(conn);
        } catch (SQLException e) {
            throw new RepositoryException("Database operation failed", e);
        } catch (ConnectionException e) {
            throw new RepositoryException("Failed to obtain connection", e);
        }
    }

    /**
     * Executes an action inside a transaction and returns the result.
     */
    protected <T> T withTransaction(ConnectionAction<T> action) throws RepositoryException {
        // Maneja commit/rollback para garantizar atomicidad del repositorio.
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                T result = action.apply(conn);
                conn.commit();
                return result;
            } catch (Exception e) {
                conn.rollback();
                throw new RepositoryException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Database operation failed", e);
        } catch (ConnectionException e) {
            throw new RepositoryException("Failed to obtain connection", e);
        }
    }

    /**
     * Executes an action inside a transaction (no return value).
     */
    protected void doWithTransaction(VoidConnectionAction action) throws RepositoryException {
        // Variante void de transacción, útil para inserciones y deletes.
        try (Connection conn = connectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.accept(conn);
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw new RepositoryException("Transaction failed", e);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Database operation failed", e);
        } catch (ConnectionException e) {
            throw new RepositoryException("Failed to obtain connection", e);
        }
    }
}
