package model.repository.impl;

import model.BettingRound;
import model.Card;
import model.PlayerRole;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import model.persistence.ConnectionFactory;
import model.persistence.RepositoryException;
import model.repository.BaseRepository;
import model.repository.GameRepository;
import util.CardSerializer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite JDBC implementation of GameRepository.
 */
public class SqliteGameRepository extends BaseRepository implements GameRepository {

    public SqliteGameRepository(ConnectionFactory connectionFactory) {
        super(connectionFactory);
    }

    @Override
    public void save(GameStateDto state) throws RepositoryException {
        doWithTransaction(conn -> {
            long stateId = upsertGameState(conn, state);
            replacePlayers(conn, stateId, state.getPlayers());
        });
    }

    @Override
    public Optional<GameStateDto> findByGameId(String gameId) throws RepositoryException {
        return withConnection(conn -> {
            Optional<Long> stateIdOpt = findStateIdByGameId(conn, gameId);
            if (stateIdOpt.isEmpty()) {
                return Optional.<GameStateDto>empty();
            }
            long stateId = stateIdOpt.get();
            GameStateDto state = loadGameState(conn, stateId);
            state.setPlayers(loadPlayers(conn, stateId));
            return Optional.of(state);
        });
    }

    @Override
    public Optional<GameStateDto> findLatest() throws RepositoryException {
        return withConnection(conn -> {
            Optional<Long> stateIdOpt = findLatestStateId(conn);
            if (stateIdOpt.isEmpty()) {
                return Optional.<GameStateDto>empty();
            }
            long stateId = stateIdOpt.get();
            GameStateDto state = loadGameState(conn, stateId);
            state.setPlayers(loadPlayers(conn, stateId));
            return Optional.of(state);
        });
    }

    @Override
    public boolean delete(String gameId) throws RepositoryException {
        return withTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM game_state WHERE game_id = ?")) {
                stmt.setString(1, gameId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    @Override
    public void saveByMachineId(String machineId, GameStateDto state) throws RepositoryException {
        doWithTransaction(conn -> {
            long stateId = upsertGameStateByMachineId(conn, machineId, state);
            replacePlayers(conn, stateId, state.getPlayers());
        });
    }

    @Override
    public Optional<GameStateDto> loadByMachineId(String machineId) throws RepositoryException {
        return withConnection(conn -> {
            Optional<Long> stateIdOpt = findStateIdByMachineId(conn, machineId);
            if (stateIdOpt.isEmpty()) {
                return Optional.<GameStateDto>empty();
            }
            long stateId = stateIdOpt.get();
            GameStateDto state = loadGameState(conn, stateId);
            state.setMachineId(machineId);
            state.setPlayers(loadPlayers(conn, stateId));
            return Optional.of(state);
        });
    }

    @Override
    public boolean deleteByMachineId(String machineId) throws RepositoryException {
        return withTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM game_state WHERE machine_id = ?")) {
                stmt.setString(1, machineId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    // ------------------------------------------------------------------
    //  Internal helpers
    // ------------------------------------------------------------------

    private long upsertGameState(Connection conn, GameStateDto state) throws SQLException {
        // Try update first
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE game_state SET pot = ?, community_cards_json = ?, " +
                "remaining_deck_json = ?, dealer_index = ?, current_phase = ?, timestamp = ?, " +
                "machine_id = ?, human_chips = ? " +
                "WHERE game_id = ?")) {
            stmt.setInt(1, state.getPot());
            stmt.setString(2, CardSerializer.toJson(state.getCommunityCards()));
            stmt.setString(3, CardSerializer.toJson(state.getRemainingDeck()));
            stmt.setInt(4, state.getDealerIndex());
            stmt.setString(5, state.getCurrentPhase().name());
            stmt.setString(6, java.time.Instant.ofEpochMilli(state.getTimestamp()).toString());
            stmt.setString(7, state.getMachineId());
            stmt.setInt(8, state.getHumanChips());
            stmt.setString(9, state.getGameId());
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                return findStateIdByGameId(conn, state.getGameId()).orElseThrow(
                    () -> new SQLException("Failed to retrieve state id after update"));
            }
        }

        // Insert new
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO game_state (game_id, pot, community_cards_json, remaining_deck_json, " +
                "dealer_index, current_phase, timestamp, machine_id, human_chips) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, state.getGameId());
            stmt.setInt(2, state.getPot());
            stmt.setString(3, CardSerializer.toJson(state.getCommunityCards()));
            stmt.setString(4, CardSerializer.toJson(state.getRemainingDeck()));
            stmt.setInt(5, state.getDealerIndex());
            stmt.setString(6, state.getCurrentPhase().name());
            stmt.setString(7, java.time.Instant.ofEpochMilli(state.getTimestamp()).toString());
            stmt.setString(8, state.getMachineId());
            stmt.setInt(9, state.getHumanChips());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert game_state");
    }

    private Optional<Long> findStateIdByGameId(Connection conn, String gameId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM game_state WHERE game_id = ?")) {
            stmt.setString(1, gameId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("id"));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Long> findLatestStateId(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                "SELECT id FROM game_state ORDER BY timestamp DESC LIMIT 1")) {
            if (rs.next()) {
                return Optional.of(rs.getLong("id"));
            }
        }
        return Optional.empty();
    }

    private Optional<Long> findStateIdByMachineId(Connection conn, String machineId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT id FROM game_state WHERE machine_id = ?")) {
            stmt.setString(1, machineId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getLong("id"));
                }
            }
        }
        return Optional.empty();
    }

    private long upsertGameStateByMachineId(Connection conn, String machineId, GameStateDto state) throws SQLException {
        // Try update first
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE game_state SET pot = ?, community_cards_json = ?, " +
                "remaining_deck_json = ?, dealer_index = ?, current_phase = ?, timestamp = ?, " +
                "human_chips = ? " +
                "WHERE machine_id = ?")) {
            stmt.setInt(1, state.getPot());
            stmt.setString(2, CardSerializer.toJson(state.getCommunityCards()));
            stmt.setString(3, CardSerializer.toJson(state.getRemainingDeck()));
            stmt.setInt(4, state.getDealerIndex());
            stmt.setString(5, state.getCurrentPhase().name());
            stmt.setString(6, java.time.Instant.ofEpochMilli(state.getTimestamp()).toString());
            stmt.setInt(7, state.getHumanChips());
            stmt.setString(8, machineId);
            int updated = stmt.executeUpdate();
            if (updated > 0) {
                return findStateIdByMachineId(conn, machineId).orElseThrow(
                    () -> new SQLException("Failed to retrieve state id after update"));
            }
        }

        // Step 2: Fallback — claim legacy row with NULL machine_id by game_id
        try (PreparedStatement stmt = conn.prepareStatement(
                "UPDATE game_state SET machine_id = ?, pot = ?, community_cards_json = ?, " +
                "remaining_deck_json = ?, dealer_index = ?, current_phase = ?, timestamp = ?, " +
                "human_chips = ? WHERE game_id = ?")) {
            stmt.setString(1, machineId);
            stmt.setInt(2, state.getPot());
            stmt.setString(3, CardSerializer.toJson(state.getCommunityCards()));
            stmt.setString(4, CardSerializer.toJson(state.getRemainingDeck()));
            stmt.setInt(5, state.getDealerIndex());
            stmt.setString(6, state.getCurrentPhase().name());
            stmt.setString(7, java.time.Instant.ofEpochMilli(state.getTimestamp()).toString());
            stmt.setInt(8, state.getHumanChips());
            stmt.setString(9, state.getGameId());
            int fallbackUpdated = stmt.executeUpdate();
            if (fallbackUpdated > 0) {
                return findStateIdByGameId(conn, state.getGameId()).orElseThrow(
                    () -> new SQLException("Failed to retrieve state id after fallback update"));
            }
        }

        // Step 3: Insert new row
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO game_state (game_id, machine_id, pot, community_cards_json, " +
                "remaining_deck_json, dealer_index, current_phase, timestamp, human_chips) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, state.getGameId());
            stmt.setString(2, machineId);
            stmt.setInt(3, state.getPot());
            stmt.setString(4, CardSerializer.toJson(state.getCommunityCards()));
            stmt.setString(5, CardSerializer.toJson(state.getRemainingDeck()));
            stmt.setInt(6, state.getDealerIndex());
            stmt.setString(7, state.getCurrentPhase().name());
            stmt.setString(8, java.time.Instant.ofEpochMilli(state.getTimestamp()).toString());
            stmt.setInt(9, state.getHumanChips());
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert game_state for machine_id: " + machineId);
    }

    private void replacePlayers(Connection conn, long stateId, List<PlayerStateDto> players) throws SQLException {
        // Delete existing players for this game state
        try (PreparedStatement stmt = conn.prepareStatement(
                "DELETE FROM game_players WHERE game_state_id = ?")) {
            stmt.setLong(1, stateId);
            stmt.executeUpdate();
        }

        // Insert new players
        try (PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO game_players (game_state_id, player_id, name, hand_json, chips, " +
                "current_bet, folded, all_in, role, ai) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (PlayerStateDto p : players) {
                stmt.setLong(1, stateId);
                stmt.setString(2, p.getPlayerId());
                stmt.setString(3, p.getName());
                stmt.setString(4, CardSerializer.toJson(p.getHand()));
                stmt.setInt(5, p.getChips());
                stmt.setInt(6, p.getCurrentBet());
                stmt.setInt(7, p.isFolded() ? 1 : 0);
                stmt.setInt(8, p.isAllIn() ? 1 : 0);
                stmt.setString(9, p.getRole() != null ? p.getRole().name() : PlayerRole.NONE.name());
                stmt.setInt(10, p.isAi() ? 1 : 0);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    private GameStateDto loadGameState(Connection conn, long stateId) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT game_id, machine_id, human_chips, pot, community_cards_json, remaining_deck_json, " +
                "dealer_index, current_phase, timestamp FROM game_state WHERE id = ?")) {
            stmt.setLong(1, stateId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Game state not found for id: " + stateId);
                }
                GameStateDto state = new GameStateDto();
                state.setGameId(rs.getString("game_id"));
                state.setMachineId(rs.getString("machine_id"));
                state.setHumanChips(rs.getInt("human_chips"));
                state.setPot(rs.getInt("pot"));
                state.setCommunityCards(parseCards(rs.getString("community_cards_json")));
                state.setRemainingDeck(parseCards(rs.getString("remaining_deck_json")));
                state.setDealerIndex(rs.getInt("dealer_index"));
                state.setCurrentPhase(BettingRound.Phase.valueOf(rs.getString("current_phase")));
                String tsStr = rs.getString("timestamp");
                if (tsStr != null) {
                    state.setTimestamp(java.time.Instant.parse(tsStr).toEpochMilli());
                }
                return state;
            }
        }
    }

    private List<PlayerStateDto> loadPlayers(Connection conn, long stateId) throws SQLException {
        List<PlayerStateDto> players = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(
                "SELECT player_id, name, hand_json, chips, current_bet, folded, all_in, role, ai " +
                "FROM game_players WHERE game_state_id = ?")) {
            stmt.setLong(1, stateId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerStateDto p = new PlayerStateDto();
                    p.setPlayerId(rs.getString("player_id"));
                    p.setName(rs.getString("name"));
                    p.setHand(parseCards(rs.getString("hand_json")));
                    p.setChips(rs.getInt("chips"));
                    p.setCurrentBet(rs.getInt("current_bet"));
                    p.setFolded(rs.getInt("folded") == 1);
                    p.setAllIn(rs.getInt("all_in") == 1);
                    p.setRole(PlayerRole.valueOf(rs.getString("role")));
                    p.setAi(rs.getInt("ai") == 1);
                    players.add(p);
                }
            }
        }
        return players;
    }

    private List<Card> parseCards(String json) {
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return new ArrayList<>();
        }
        return CardSerializer.fromJson(json);
    }
}
