package model.persistence;

/**
 * Excepción cuando falla una operación del repositorio (CRUD).
 */
public class RepositoryException extends PersistenceException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
