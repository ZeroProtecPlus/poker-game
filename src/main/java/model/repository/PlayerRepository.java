package model.repository;

import model.User;
import model.persistence.RepositoryException;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio de jugadores persistidos.
 */
public interface PlayerRepository {

    /**
     * Guarda o actualiza un jugador.
     */
    User save(User player) throws RepositoryException;

    /**
     * Busca por playerId de negocio (UUID).
     */
    Optional<User> findById(String playerId) throws RepositoryException;

    /**
     * Busca por nombre visible (para reusar perfiles locales).
     */
    Optional<User> findByName(String name) throws RepositoryException;

    /**
     * Borra un jugador por playerId.
     */
    boolean delete(String playerId) throws RepositoryException;

    /**
     * Devuelve todos los jugadores persistidos.
     */
    List<User> findAll() throws RepositoryException;
}
