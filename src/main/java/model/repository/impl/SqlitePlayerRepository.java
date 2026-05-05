package model.repository.impl;

import model.User;
import model.persistence.ConnectionFactory;
import model.persistence.RepositoryException;
import model.repository.BaseRepository;
import model.repository.PlayerRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Implementación SQLite de PlayerRepository.
 * Upsert por player_id para mantener unicidad.
 */
public class SqlitePlayerRepository extends BaseRepository implements PlayerRepository {

    public SqlitePlayerRepository(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    @Override
    public User save(User player) throws RepositoryException {
        // Upsert simple para evitar duplicados por id.
        return withTransaction(conn -> {
            Optional<User> existing = findByIdInternal(conn, player.getPlayerId());
            if (existing.isPresent()) {
                update(conn, player);
            } else {
                insert(conn, player);
            }
            return player;
        });
    }

    @Override
    public Optional<User> findById(String playerId) throws RepositoryException {
        return withConnection(conn -> findByIdInternal(conn, playerId));
    }

    @Override
    public Optional<User> findByName(String name) throws RepositoryException {
        // Búsqueda por nombre para reusar perfiles locales.
        return withConnection(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT player_id, name, chips FROM players WHERE name = ?")) {
                stmt.setString(1, name);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs));
                    }
                    return Optional.<User>empty();
                }
            }
        });
    }

    @Override
    public boolean delete(String playerId) throws RepositoryException {
        return withTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM players WHERE player_id = ?")) {
                stmt.setString(1, playerId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public List<User> findAll() throws RepositoryException {
        // Carga simple sin paginado, usada para pantallas de administración.
        return withConnection(conn -> {
            List<User> players = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT player_id, name, chips FROM players");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    players.add(mapRow(rs));
                }
            }
            return players;
        });
    }

    private Optional<User> findByIdInternal(Connection conn, String playerId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT player_id, name, chips FROM players WHERE player_id = ?")) {
            stmt.setString(1, playerId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
                return Optional.empty();
            }
        }
    }

    private void insert(Connection conn, User player) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO players (player_id, name, chips) VALUES (?, ?, ?)")) {
            stmt.setString(1, player.getPlayerId());
            stmt.setString(2, player.getName());
            stmt.setInt(3, player.getChips());
            stmt.executeUpdate();
        }
    }

    private void update(Connection conn, User player) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE players SET name = ?, chips = ? WHERE player_id = ?")) {
            stmt.setString(1, player.getName());
            stmt.setInt(2, player.getChips());
            stmt.setString(3, player.getPlayerId());
            stmt.executeUpdate();
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User(rs.getString("player_id"), rs.getString("name"));
        user.setChips(rs.getInt("chips"));
        return user;
    }
}
