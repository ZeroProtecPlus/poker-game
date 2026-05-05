package model.persistence;

/**
 * Excepción cuando no se puede abrir una conexión a la base.
 */
public class ConnectionException extends PersistenceException {

    public ConnectionException(String message) {
        super(message);
    }

    public ConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
