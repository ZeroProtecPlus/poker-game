package model.persistence;

/**
 * Exception thrown when an expected entity is not found in the database.
 */
public class EntityNotFoundException extends PersistenceException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
