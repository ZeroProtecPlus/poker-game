package controller;

import model.AIPlayer;
import model.BettingRound;
import model.Card;
import model.PokerGame;
import model.ShowdownResult;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import model.persistence.MachineIdProvider;
import model.persistence.RepositoryException;
import model.repository.GameRepository;
import model.repository.PlayerRepository;
import model.repository.impl.SqlitePlayerRepository;
import network.contracts.JoinDecision;
import network.host.HostJoinHandler;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;
import view.GameView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Tests for the resume flow using machine_id persistence.
 * Verifies: T-15 (checkForSavedGame), T-16 (offerResume with machine_id),
 * T-18 (loadGameByMachineId), T-19 (deleteSavedGame).
 */
class GameControllerResumeTest {

    // ── Test: Resume restores saved chips ──────────────────────────────────

    @Test
    void shouldRestoreChipsWhenResumingSavedGame() {
        String machineId = "test-machine-001";
        int savedChips = 5000;

        MockGameRepository repo = new MockGameRepository();
        repo.savedState = createSavedState(machineId, savedChips);
        repo.loadByMachineIdResult = Optional.of(repo.savedState);

        ResumeTestView view = new ResumeTestView(true); // accept resume
        GameController controller = createControllerWithMocks(view, repo, machineId);
        controller.createNewPlayer();
        // Resume flow is inside createNewGame(), not createNewPlayer()
        invokeCreateNewGame(controller);

        Assertions.assertTrue(view.askResumeGameCalled, "askResumeGame should be called when saved game exists");
        Assertions.assertEquals(savedChips, view.resumeChipsArg,
            "askResumeGame should receive saved chips");
        Assertions.assertFalse(repo.deleteByMachineIdCalled,
            "deleteByMachineId should NOT be called when user accepts resume");

        model.User user = readUser(controller);
        Assertions.assertNotNull(user, "user should be created");
        Assertions.assertEquals(savedChips, user.getNumbChips(),
            "user chips should be restored from saved game");
    }

    // ── Test: Decline resume deletes save ──────────────────────────────────

    @Test
    void shouldDeleteSavedGameWhenDecliningResume() {
        String machineId = "test-machine-002";
        int savedChips = 3000;

        MockGameRepository repo = new MockGameRepository();
        repo.savedState = createSavedState(machineId, savedChips);
        repo.loadByMachineIdResult = Optional.of(repo.savedState);

        ResumeTestView view = new ResumeTestView(false); // decline resume
        GameController controller = createControllerWithMocks(view, repo, machineId);
        controller.createNewPlayer();
        invokeCreateNewGame(controller);

        Assertions.assertTrue(view.askResumeGameCalled, "askResumeGame should be called when saved game exists");
        Assertions.assertTrue(repo.deleteByMachineIdCalled,
            "deleteByMachineId should be called when user declines resume");
        Assertions.assertEquals(machineId, repo.deletedMachineId,
            "deleteByMachineId should be called with correct machine ID");
    }

    // ── Test: No saved game skips dialog ───────────────────────────────────

    @Test
    void shouldSkipResumeDialogWhenNoSavedGame() {
        String machineId = "test-machine-003";

        MockGameRepository repo = new MockGameRepository();
        repo.loadByMachineIdResult = Optional.empty();

        ResumeTestView view = new ResumeTestView(true);
        GameController controller = createControllerWithMocks(view, repo, machineId);
        controller.createNewPlayer();
        invokeCreateNewGame(controller);

        Assertions.assertFalse(view.askResumeGameCalled,
            "askResumeGame should NOT be called when no saved game exists");
        Assertions.assertFalse(repo.deleteByMachineIdCalled,
            "deleteByMachineId should NOT be called when no saved game");
    }

    // ── Test: buildGameStateDto includes machineId + humanChips ────────────

    @Test
    void shouldBuildDtoWithMachineIdAndHumanChips() {
        String machineId = "test-machine-004";

        MockGameRepository repo = new MockGameRepository();
        repo.loadByMachineIdResult = Optional.empty();

        ResumeTestView view = new ResumeTestView(true);
        GameController controller = createControllerWithMocks(view, repo, machineId);
        controller.createNewPlayer();
        invokeCreateNewGame(controller);

        // playOneHand is called by createNewGame loop; inspect saved state
        Assertions.assertNotNull(repo.lastSavedMachineId,
            "saveByMachineId should have been called");
        Assertions.assertEquals(machineId, repo.lastSavedMachineId,
            "saveByMachineId should use machine ID");
        Assertions.assertNotNull(repo.lastSavedState,
            "GameStateDto should have been saved");
        Assertions.assertTrue(repo.lastSavedState.getHumanChips() > 0,
            "humanChips should be set in saved DTO");
        Assertions.assertEquals(machineId, repo.lastSavedState.getMachineId(),
            "machineId should be set in saved DTO");
    }

    // ── Test: Full flow — new game → save → resume → new game deletes old ──

    @Test
    void shouldFullFlowNewGameSaveResumeNewGameDeletesOld() {
        String machineId = "test-machine-full";

        // Phase 1: New game, no saved state → starts fresh
        MockGameRepository repo = new MockGameRepository();
        repo.loadByMachineIdResult = Optional.empty();
        ResumeTestView view1 = new ResumeTestView(true);
        GameController controller1 = createControllerWithMocks(view1, repo, machineId);
        controller1.createNewPlayer();
        invokeCreateNewGame(controller1);

        Assertions.assertFalse(view1.askResumeGameCalled, "first run should not show resume dialog");
        Assertions.assertNotNull(repo.lastSavedMachineId, "first game should save state");
        Assertions.assertEquals(machineId, repo.lastSavedMachineId, "save should use machine ID");

        // Phase 2: New game, saved state exists → accept resume
        repo.loadByMachineIdResult = Optional.of(repo.lastSavedState);
        int savedChips = repo.lastSavedState.getHumanChips();
        ResumeTestView view2 = new ResumeTestView(true); // accept resume
        GameController controller2 = createControllerWithMocks(view2, repo, machineId);
        controller2.createNewPlayer();
        invokeCreateNewGame(controller2);

        Assertions.assertTrue(view2.askResumeGameCalled, "second run should show resume dialog");
        Assertions.assertEquals(savedChips, view2.resumeChipsArg,
            "resume dialog should show saved chips");
        Assertions.assertFalse(repo.deleteByMachineIdCalled, "accepting resume should NOT delete save");

        model.User user2 = readUser(controller2);
        Assertions.assertEquals(savedChips, user2.getNumbChips(),
            "user chips should be restored");

        // Phase 3: New game, saved state exists → decline resume → deletes old save
        repo.deleteByMachineIdCalled = false;
        repo.deletedMachineId = null;
        ResumeTestView view3 = new ResumeTestView(false); // decline resume
        GameController controller3 = createControllerWithMocks(view3, repo, machineId);
        controller3.createNewPlayer();
        invokeCreateNewGame(controller3);

        Assertions.assertTrue(view3.askResumeGameCalled, "third run should show resume dialog");
        Assertions.assertTrue(repo.deleteByMachineIdCalled, "declining resume should delete old save");
        Assertions.assertEquals(machineId, repo.deletedMachineId, "delete should use correct machine ID");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static GameStateDto createSavedState(String machineId, int chips) {
        GameStateDto state = new GameStateDto();
        state.setGameId("test_latest");
        state.setMachineId(machineId);
        state.setHumanChips(chips);
        state.setPot(0);
        state.setCommunityCards(new ArrayList<>());
        state.setRemainingDeck(new ArrayList<>());
        state.setDealerIndex(0);
        state.setCurrentPhase(BettingRound.Phase.PREFLOP);

        PlayerStateDto human = new PlayerStateDto();
        human.setPlayerId("human-1");
        human.setName("TestPlayer");
        human.setChips(chips);
        human.setAi(false);
        human.setHand(new ArrayList<>());
        human.setCurrentBet(0);
        human.setFolded(false);
        human.setAllIn(false);

        PlayerStateDto ai = new PlayerStateDto();
        ai.setPlayerId("ai-1");
        ai.setName("Bot1");
        ai.setChips(10000);
        ai.setAi(true);
        ai.setHand(new ArrayList<>());
        ai.setCurrentBet(0);
        ai.setFolded(false);
        ai.setAllIn(false);

        state.setPlayers(List.of(human, ai));
        return state;
    }

    private static GameController createControllerWithMocks(
        ResumeTestView view, MockGameRepository repo, String machineId
    ) {
        TestMachineIdProvider idProvider = new TestMachineIdProvider(machineId);
        GameController.Repositories repos = new GameController.Repositories(
            new SqlitePlayerRepository(
                model.persistence.ConnectionFactory.forMemory()
            ),
            repo
        );
        HostJoinHandler joinHandler = new TestJoinHandler();
        return new GameController(view, joinHandler, repos, idProvider);
    }

    private static model.User readUser(GameController controller) {
        try {
            Field f = GameController.class.getDeclaredField("newPlayer");
            f.setAccessible(true);
            return (model.User) f.get(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to read user", ex);
        }
    }

    private static void invokePlayOneHand(GameController controller) {
        try {
            Method m = GameController.class.getDeclaredMethod("playOneHand");
            m.setAccessible(true);
            m.invoke(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke playOneHand", ex);
        }
    }

    private static void invokeCreateNewGame(GameController controller) {
        try {
            Method m = GameController.class.getDeclaredMethod("createNewGame");
            m.setAccessible(true);
            m.invoke(controller);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("failed to invoke createNewGame", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ── Test Doubles ───────────────────────────────────────────────────────

    private static final class TestMachineIdProvider implements MachineIdProvider {
        private final String machineId;
        TestMachineIdProvider(String machineId) { this.machineId = machineId; }
        @Override public String getMachineId() { return machineId; }
    }

    private static final class MockGameRepository implements GameRepository {
        GameStateDto savedState;
        Optional<GameStateDto> loadByMachineIdResult = Optional.empty();
        String lastSavedMachineId;
        GameStateDto lastSavedState;
        boolean deleteByMachineIdCalled;
        String deletedMachineId;

        @Override public void save(GameStateDto state) {
            throw new UnsupportedOperationException("use saveByMachineId");
        }
        @Override public Optional<GameStateDto> findByGameId(String gameId) {
            return Optional.empty();
        }
        @Override public Optional<GameStateDto> findLatest() {
            return Optional.empty();
        }
        @Override public void saveByMachineId(String machineId, GameStateDto state) {
            this.lastSavedMachineId = machineId;
            this.lastSavedState = state;
        }
        @Override public Optional<GameStateDto> loadByMachineId(String machineId) {
            return loadByMachineIdResult;
        }
        @Override public boolean deleteByMachineId(String machineId) {
            this.deleteByMachineIdCalled = true;
            this.deletedMachineId = machineId;
            return true;
        }
        @Override public boolean delete(String gameId) { return false; }
    }

    private static final class TestJoinHandler extends HostJoinHandler {
        TestJoinHandler() { super(new NameValidator(), new SessionNameRegistry()); }
        @Override
        public JoinDecision handleJoin(network.contracts.JoinRequest request) {
            return JoinDecision.accepted("test-player-id", request.getProposedName());
        }
    }

    private static final class ResumeTestView extends GameView {
        private final boolean acceptResume;
        boolean askResumeGameCalled;
        int resumeChipsArg;
        int waitForPlayerActionCalls;
        boolean resultShown;

        ResumeTestView(boolean acceptResume) {
            super(false);
            this.acceptResume = acceptResume;
        }

        @Override public boolean awaitUiReady(long timeoutMs) { return true; }
        @Override public String getUserName() { return "ResumeTestUser"; }
        @Override public void showJoinRejectionMessage(String m) {}
        @Override public boolean askRetryJoin() { return false; }
        @Override public void showUserChips(String n, int c) {}
        @Override public void clearTableForNewHand() {}
        @Override public void setGameActive(boolean active) {}
        @Override public void showPlayerHand(ArrayList<Card> h) {}
        @Override public void showCommunityCards(ArrayList<Card> c, ArrayList<Card> h, int n) {}
        @Override public void showRoles(AIPlayer.Role r, List<AIPlayer> a) {}
        @Override public void showPot(int p) {}
        @Override public void showAIActions(List<String> l) {}
        @Override public void showPlayerFolded() {}

        @Override public BettingRound.Action waitForPlayerAction(
            BettingRound round, int playerCurrentBet
        ) {
            waitForPlayerActionCalls++;
            return BettingRound.Action.FOLD;
        }

        @Override public BettingRound.Action waitForPlayerAction(
            BettingRound round, int playerCurrentBet, long timeoutMs
        ) {
            waitForPlayerActionCalls++;
            return BettingRound.Action.FOLD;
        }

        @Override public int getPlayerBetAmount(int min, int max) { return min; }

        @Override public void showResult(ArrayList<Card> c, ArrayList<Card> h,
                                         ShowdownResult r, int p) {
            resultShown = true;
        }
        @Override public void showResult(ArrayList<Card> c, ArrayList<Card> h,
                                         PokerGame.HandRank rank, int p,
                                         boolean hw, String awn) {
            resultShown = true;
        }

        @Override public boolean askResumeGame(int savedChips) {
            askResumeGameCalled = true;
            resumeChipsArg = savedChips;
            return acceptResume;
        }

        @Override public boolean askPlayAgain(int chips) { return false; }
        @Override public void showGameOver(int chips) {}
        @Override public void requestGracefulShutdown() {}
    }
}
