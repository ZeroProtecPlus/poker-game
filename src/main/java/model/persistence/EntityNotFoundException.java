package model.persistence;

/**
 * Excepción cuando no se encuentra una entidad esperada en DB.
 */
public class EntityNotFoundException extends PersistenceException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
