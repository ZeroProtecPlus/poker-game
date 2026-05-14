package model.persistence;

/**
 * Provides a persistent machine identity (UUID v4).
 * The same ID is returned across application restarts.
 */
public interface MachineIdProvider {

    /**
     * Returns the persistent machine UUID.
     * Generates and persists on first call or if file is missing/corrupted.
     *
     * @return a valid UUID v4 string
     */
    String getMachineId();
}
