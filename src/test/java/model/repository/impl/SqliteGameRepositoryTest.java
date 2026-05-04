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
