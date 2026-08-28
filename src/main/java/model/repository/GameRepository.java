package model.repository;

import model.dto.GameStateDto;
import model.persistence.RepositoryException;

import java.util.Optional;

/**
 * Repository for game state persistence.
 */
public interface GameRepository {

    /**
     * Saves or updates a game state snapshot.
     *
     * @param state the game state to save
     * @throws RepositoryException if the operation fails
     * @deprecated use saveByMachineId instead
     */
    @Deprecated
    void save(GameStateDto state) throws RepositoryException;

    /**
     * Finds a game state by its business gameId.
     *
     * @param gameId the game's identifier
     * @return optional containing the game state if found
     * @throws RepositoryException if the operation fails
     */
    Optional<GameStateDto> findByGameId(String gameId) throws RepositoryException;

    /**
     * Finds the most recently saved game state.
     *
     * @return optional containing the latest game state
     * @throws RepositoryException if the operation fails
     * @deprecated use loadByMachineId instead
     */
    @Deprecated
    Optional<GameStateDto> findLatest() throws RepositoryException;

    /**
     * Saves or updates game state scoped to machine ID.
     *
     * @param machineId the machine identifier
     * @param state     the game state to save
     * @throws RepositoryException if the operation fails
     */
    void saveByMachineId(String machineId, GameStateDto state) throws RepositoryException;

    /**
     * Loads game state by machine ID. Returns empty if none saved.
     *
     * @param machineId the machine identifier
     * @return optional containing the game state if found
     * @throws RepositoryException if the operation fails
     */
    Optional<GameStateDto> loadByMachineId(String machineId) throws RepositoryException;

    /**
     * Deletes game state and associated players by machine ID.
     *
     * @param machineId the machine identifier
     * @return true if a game state was deleted
     * @throws RepositoryException if the operation fails
     */
    boolean deleteByMachineId(String machineId) throws RepositoryException;

    /**
     * Deletes a game state and all associated player states / actions.
     *
     * @param gameId the game's identifier
     * @return true if a game state was deleted
     * @throws RepositoryException if the operation fails
     * @deprecated use deleteByMachineId instead
     */
    @Deprecated
    boolean delete(String gameId) throws RepositoryException;
}
