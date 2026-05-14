package model.repository;

import model.User;
import model.persistence.RepositoryException;

import java.util.List;
import java.util.Optional;

/**
 * Repository for player persistence.
 */
public interface PlayerRepository {

    /**
     * Saves or updates a player.
     *
     * @param player the player to save
     * @return the saved player (with generated id if new)
     * @throws RepositoryException if the operation fails
     */
    User save(User player) throws RepositoryException;

    /**
     * Finds a player by their business playerId.
     *
     * @param playerId the player's UUID string
     * @return optional containing the player if found
     * @throws RepositoryException if the operation fails
     */
    Optional<User> findById(String playerId) throws RepositoryException;

    /**
     * Finds a player by their display name.
     *
     * @param name the player's name
     * @return optional containing the player if found
     * @throws RepositoryException if the operation fails
     */
    Optional<User> findByName(String name) throws RepositoryException;

    /**
     * Deletes a player by their business playerId.
     *
     * @param playerId the player's UUID string
     * @return true if a player was deleted
     * @throws RepositoryException if the operation fails
     */
    boolean delete(String playerId) throws RepositoryException;

    /**
     * Returns all persisted players.
     *
     * @return list of all players
     * @throws RepositoryException if the operation fails
     */
    List<User> findAll() throws RepositoryException;
}
