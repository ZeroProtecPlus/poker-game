package model.repository.impl;

import model.BettingRound;
import model.Card;
import model.PlayerRole;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import model.persistence.ConnectionFactory;
import model.persistence.DatabaseBootstrapper;
import model.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SqliteGameRepository.
 */
class SqliteGameRepositoryTest {

    private GameRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws Exception {
        // Explicitly load SQLite driver for Java 9+ module system
        Class.forName("org.sqlite.JDBC");
        Path dbFile = tempDir.resolve("game.db");
        ConnectionFactory factory = new ConnectionFactory(dbFile.toString());
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper(factory);
        bootstrapper.bootstrap();
        repository = new SqliteGameRepository(factory);
    }

    @Test
    void shouldSaveAndFindGameState() throws Exception {
        GameStateDto state = createSampleState("game-1");
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-1");

        assertTrue(found.isPresent(), "game state should be found");
        assertEquals("game-1", found.get().getGameId());
        assertEquals(150, found.get().getPot());
        assertEquals(BettingRound.Phase.FLOP, found.get().getCurrentPhase());
        assertEquals(1, found.get().getDealerIndex());
        assertEquals(2, found.get().getCommunityCards().size());
    }

    @Test
    void shouldUpdateExistingGameState() throws Exception {
        GameStateDto state = createSampleState("game-2");
        repository.save(state);

        state.setPot(300);
        state.setCurrentPhase(BettingRound.Phase.TURN);
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-2");
        assertTrue(found.isPresent());
        assertEquals(300, found.get().getPot());
        assertEquals(BettingRound.Phase.TURN, found.get().getCurrentPhase());
    }

    @Test
    void shouldReturnEmptyForUnknownGameId() throws Exception {
        Optional<GameStateDto> found = repository.findByGameId("non-existent");
        assertTrue(found.isEmpty(), "should return empty for unknown game id");
    }

    @Test
    void shouldFindLatestGameState() throws Exception {
        repository.save(createSampleState("game-a"));
        Thread.sleep(10); // ensure different timestamps
        repository.save(createSampleState("game-b"));

        Optional<GameStateDto> latest = repository.findLatest();

        assertTrue(latest.isPresent(), "latest should be found");
        assertEquals("game-b", latest.get().getGameId());
    }

    @Test
    void shouldDeleteGameState() throws Exception {
        GameStateDto state = createSampleState("game-3");
        repository.save(state);

        boolean deleted = repository.delete("game-3");

        assertTrue(deleted, "delete should return true");
        assertTrue(repository.findByGameId("game-3").isEmpty(), "game should no longer exist");
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentGame() throws Exception {
        boolean deleted = repository.delete("no-game");
        assertFalse(deleted, "delete should return false for unknown game");
    }

    @Test
    void shouldPersistPlayerStates() throws Exception {
        GameStateDto state = createSampleState("game-4");
        PlayerStateDto human = new PlayerStateDto();
        human.setPlayerId("p1");
        human.setName("Alice");
        human.setChips(5000);
        human.setCurrentBet(50);
        human.setFolded(false);
        human.setAllIn(false);
        human.setRole(PlayerRole.DEALER);
        human.setHand(List.of(new Card("A", "♠"), new Card("K", "♥")));
        human.setAi(false);

        PlayerStateDto ai = new PlayerStateDto();
        ai.setPlayerId("p2");
        ai.setName("Bot");
        ai.setChips(3000);
        ai.setCurrentBet(100);
        ai.setFolded(true);
        ai.setAllIn(true);
        ai.setRole(PlayerRole.BIG_BLIND);
        ai.setHand(List.of(new Card("2", "♦")));
        ai.setAi(true);

        state.setPlayers(List.of(human, ai));
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-4");
        assertTrue(found.isPresent());
        List<PlayerStateDto> players = found.get().getPlayers();
        assertEquals(2, players.size(), "should persist 2 players");

        PlayerStateDto loadedHuman = players.get(0);
        assertEquals("p1", loadedHuman.getPlayerId());
        assertEquals("Alice", loadedHuman.getName());
        assertEquals(5000, loadedHuman.getChips());
        assertEquals(50, loadedHuman.getCurrentBet());
        assertFalse(loadedHuman.isFolded());
        assertFalse(loadedHuman.isAllIn());
        assertEquals(PlayerRole.DEALER, loadedHuman.getRole());
        assertEquals(2, loadedHuman.getHand().size());

        PlayerStateDto loadedAi = players.get(1);
        assertEquals("p2", loadedAi.getPlayerId());
        assertTrue(loadedAi.isFolded());
        assertTrue(loadedAi.isAllIn());
        assertEquals(PlayerRole.BIG_BLIND, loadedAi.getRole());
        assertTrue(loadedAi.isAi(), "ai flag should persist for AI player");

        assertFalse(loadedHuman.isAi(), "ai flag should be false for human player");
    }

    @Test
    void shouldReplacePlayersOnUpdate() throws Exception {
        GameStateDto state = createSampleState("game-5");
        PlayerStateDto p1 = new PlayerStateDto();
        p1.setPlayerId("p1");
        p1.setName("Alice");
        p1.setChips(1000);
        p1.setHand(List.of());
        p1.setRole(PlayerRole.NONE);
        p1.setAi(false);

        state.setPlayers(List.of(p1));
        repository.save(state);

        // Update with different players
        PlayerStateDto p2 = new PlayerStateDto();
        p2.setPlayerId("p2");
        p2.setName("Bob");
        p2.setChips(2000);
        p2.setHand(List.of());
        p2.setRole(PlayerRole.NONE);
        p2.setAi(false);

        state.setPlayers(List.of(p2));
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-5");
        assertTrue(found.isPresent());
        List<PlayerStateDto> players = found.get().getPlayers();
        assertEquals(1, players.size(), "should have replaced players");
        assertEquals("p2", players.get(0).getPlayerId());
    }

    @Test
    void shouldPersistRemainingDeck() throws Exception {
        GameStateDto state = createSampleState("game-6");
        state.setRemainingDeck(List.of(new Card("A", "♠"), new Card("K", "♥"), new Card("Q", "♦")));
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-6");
        assertTrue(found.isPresent());
        assertEquals(3, found.get().getRemainingDeck().size(), "remaining deck should persist");
    }

    @Test
    void shouldHandleEmptyCardsAndPlayers() throws Exception {
        GameStateDto state = new GameStateDto();
        state.setGameId("game-7");
        state.setPot(0);
        state.setDealerIndex(0);
        state.setCurrentPhase(BettingRound.Phase.PREFLOP);
        state.setCommunityCards(List.of());
        state.setRemainingDeck(List.of());
        state.setPlayers(List.of());
        repository.save(state);

        Optional<GameStateDto> found = repository.findByGameId("game-7");
        assertTrue(found.isPresent());
        assertTrue(found.get().getCommunityCards().isEmpty());
        assertTrue(found.get().getRemainingDeck().isEmpty());
        assertTrue(found.get().getPlayers().isEmpty());
    }

    // ------------------------------------------------------------------
    //  Machine ID repository operations
    // ------------------------------------------------------------------

    @Test
    void shouldSaveAndLoadByMachineId() throws Exception {
        GameStateDto state = createSampleState("game-m1");
        state.setMachineId("machine-001");
        state.setHumanChips(5000);
        repository.saveByMachineId("machine-001", state);

        Optional<GameStateDto> found = repository.loadByMachineId("machine-001");

        assertTrue(found.isPresent(), "game state should be found by machine_id");
        assertEquals("machine-001", found.get().getMachineId());
        assertEquals(5000, found.get().getHumanChips(), "human_chips should persist");
        assertEquals(150, found.get().getPot());
        assertEquals(BettingRound.Phase.FLOP, found.get().getCurrentPhase());
    }

    @Test
    void shouldReturnEmptyForUnknownMachineId() throws Exception {
        Optional<GameStateDto> found = repository.loadByMachineId("non-existent-machine");
        assertTrue(found.isEmpty(), "should return empty for unknown machine_id");
    }

    @Test
    void shouldDeleteByMachineId() throws Exception {
        GameStateDto state = createSampleState("game-m2");
        state.setMachineId("machine-002");
        state.setHumanChips(3000);
        repository.saveByMachineId("machine-002", state);

        boolean deleted = repository.deleteByMachineId("machine-002");

        assertTrue(deleted, "delete should return true");
        assertTrue(repository.loadByMachineId("machine-002").isEmpty(),
                "game should no longer exist by machine_id");
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentMachineId() throws Exception {
        boolean deleted = repository.deleteByMachineId("no-machine");
        assertFalse(deleted, "delete should return false for unknown machine_id");
    }

    @Test
    void shouldUpsertByMachineId() throws Exception {
        GameStateDto state = createSampleState("game-m3");
        state.setMachineId("machine-003");
        state.setHumanChips(4000);
        repository.saveByMachineId("machine-003", state);

        // Second save with same machine_id should update, not duplicate
        state.setPot(500);
        state.setHumanChips(4500);
        state.setCurrentPhase(BettingRound.Phase.TURN);
        repository.saveByMachineId("machine-003", state);

        Optional<GameStateDto> found = repository.loadByMachineId("machine-003");
        assertTrue(found.isPresent());
        assertEquals(500, found.get().getPot(), "pot should be updated");
        assertEquals(4500, found.get().getHumanChips(), "human_chips should be updated");
        assertEquals(BettingRound.Phase.TURN, found.get().getCurrentPhase());
    }

    @Test
    void shouldPersistHumanChipsThroughRoundTrip() throws Exception {
        GameStateDto state = createSampleState("game-m4");
        state.setMachineId("machine-004");
        state.setHumanChips(7500);
        repository.saveByMachineId("machine-004", state);

        Optional<GameStateDto> found = repository.loadByMachineId("machine-004");
        assertTrue(found.isPresent());
        assertEquals(7500, found.get().getHumanChips(),
                "human_chips should survive save→load round-trip");
    }

    @Test
    void shouldPersistAiFlagThroughMachineIdRoundTrip() throws Exception {
        GameStateDto state = createSampleState("game-m5");
        state.setMachineId("machine-005");

        PlayerStateDto human = new PlayerStateDto();
        human.setPlayerId("p1");
        human.setName("Alice");
        human.setChips(5000);
        human.setRole(PlayerRole.DEALER);
        human.setHand(List.of());
        human.setAi(false);

        PlayerStateDto ai = new PlayerStateDto();
        ai.setPlayerId("p2");
        ai.setName("Bot");
        ai.setChips(3000);
        ai.setRole(PlayerRole.BIG_BLIND);
        ai.setHand(List.of());
        ai.setAi(true);

        state.setPlayers(List.of(human, ai));
        repository.saveByMachineId("machine-005", state);

        Optional<GameStateDto> found = repository.loadByMachineId("machine-005");
        assertTrue(found.isPresent());
        List<PlayerStateDto> players = found.get().getPlayers();
        assertEquals(2, players.size());

        assertFalse(players.get(0).isAi(), "human player ai flag should be false");
        assertTrue(players.get(1).isAi(), "AI player ai flag should be true");
    }

    @Test
    void shouldDeletePlayersWhenDeletingByMachineId() throws Exception {
        GameStateDto state = createSampleState("game-m6");
        state.setMachineId("machine-006");

        PlayerStateDto p = new PlayerStateDto();
        p.setPlayerId("p1");
        p.setName("Alice");
        p.setChips(5000);
        p.setRole(PlayerRole.NONE);
        p.setHand(List.of());
        p.setAi(false);
        state.setPlayers(List.of(p));

        repository.saveByMachineId("machine-006", state);
        repository.deleteByMachineId("machine-006");

        // Verify no orphaned players remain
        Optional<GameStateDto> found = repository.loadByMachineId("machine-006");
        assertTrue(found.isEmpty(), "game state should be deleted");
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private GameStateDto createSampleState(String gameId) {
        GameStateDto state = new GameStateDto();
        state.setGameId(gameId);
        state.setPot(150);
        state.setCommunityCards(List.of(new Card("A", "♠"), new Card("K", "♥")));
        state.setRemainingDeck(List.of());
        state.setDealerIndex(1);
        state.setCurrentPhase(BettingRound.Phase.FLOP);
        state.setPlayers(List.of());
        return state;
    }
}
