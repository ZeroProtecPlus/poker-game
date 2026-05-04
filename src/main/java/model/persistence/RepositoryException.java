package model.persistence;

/**
 * Exception thrown when a repository operation fails (query, insert, update, delete).
 */
public class RepositoryException extends PersistenceException {

    public RepositoryException(String message) {
        super(message);
    }

    public RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
