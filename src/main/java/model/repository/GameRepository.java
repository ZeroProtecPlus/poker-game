package model.repository;

import model.dto.GameStateDto;
import model.persistence.RepositoryException;

import java.util.Optional;

/**
 * Repositorio para snapshots de estado de juego.
 */
public interface GameRepository {

    /**
     * Guarda o actualiza un snapshot de juego.
     *
     * @param state estado a persistir
     * @deprecated usar saveByMachineId
     */
    @Deprecated
    void save(GameStateDto state) throws RepositoryException;

    /**
     * Busca un snapshot por gameId de negocio.
     *
     * @param gameId identificador del juego
     * @return optional con el estado si existe
     */
    Optional<GameStateDto> findByGameId(String gameId) throws RepositoryException;

    /**
     * Devuelve el último snapshot guardado (por timestamp).
     *
     * @deprecated usar loadByMachineId
     */
    @Deprecated
    Optional<GameStateDto> findLatest() throws RepositoryException;

    /**
     * Guarda/actualiza estado asociado a una máquina (machineId).
     */
    void saveByMachineId(String machineId, GameStateDto state) throws RepositoryException;

    /**
     * Carga estado por machineId. Devuelve empty si no hay save.
     */
    Optional<GameStateDto> loadByMachineId(String machineId) throws RepositoryException;

    /**
     * Borra estado y jugadores asociados por machineId.
     *
     * @return true si se borró algo
     */
    boolean deleteByMachineId(String machineId) throws RepositoryException;

    /**
     * Borra un snapshot por gameId.
     *
     * @deprecated usar deleteByMachineId
     */
    @Deprecated
    boolean delete(String gameId) throws RepositoryException;
}
