package model.persistence;

/**
 * Exception thrown when a database connection cannot be established or is lost.
 */
public class ConnectionException extends PersistenceException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
