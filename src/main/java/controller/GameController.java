package controller;

import model.*;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import model.persistence.DatabaseBootstrapper;
import model.persistence.FileMachineIdProvider;
import model.persistence.MachineIdProvider;
import model.persistence.RepositoryException;
import model.repository.GameRepository;
import model.repository.PlayerRepository;
import model.repository.impl.SqliteGameRepository;
import model.repository.impl.SqlitePlayerRepository;
import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.contracts.RejectReason;
import network.host.HostJoinHandler;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;
import util.CardSerializer;
import view.GameView;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GameController {

    private PokerGame pokerGame;
    private String playerId;
    private String userNamePlayer;
    private User newPlayer;
    private final GameView newGame;
    private final HostJoinHandler hostJoinHandler;
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final MachineIdProvider machineIdProvider;

    public GameController() {
        this(new GameView(), createDefaultHostJoinHandler(), createDefaultRepositories());
    }

    protected GameController(GameView gameView) {
        this(gameView, createDefaultHostJoinHandler(), createDefaultRepositories());
    }

    protected GameController(GameView gameView, HostJoinHandler hostJoinHandler) {
        this(gameView, hostJoinHandler, createDefaultRepositories());
    }

    protected GameController(GameView gameView, HostJoinHandler hostJoinHandler, Repositories repositories) {
        this(gameView, hostJoinHandler, repositories, FileMachineIdProvider.INSTANCE);
    }

    protected GameController(GameView gameView, HostJoinHandler hostJoinHandler, Repositories repositories, MachineIdProvider machineIdProvider) {
        this.newGame = gameView;
        this.hostJoinHandler = hostJoinHandler;
        this.playerRepository = repositories.playerRepository();
        this.gameRepository = repositories.gameRepository();
        this.machineIdProvider = machineIdProvider;
        this.newGame.awaitUiReady(this.newGame.getUiSyncTimeoutMs());
    }

    private static Repositories createDefaultRepositories() {
        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper();
        try {
            bootstrapper.bootstrap();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to bootstrap database", e);
        }
        return new Repositories(
            new SqlitePlayerRepository(bootstrapper.getConnectionFactory()),
            new SqliteGameRepository(bootstrapper.getConnectionFactory())
        );
    }

    public record Repositories(PlayerRepository playerRepository, GameRepository gameRepository) {}

    public void createNewPlayer() {
        while (true) {
            JoinDecision decision = requestJoinAdmission(newGame.getUserName());
            if (decision.isAccepted()) {
                applyAcceptedJoinDecision(decision);
                loadOrCreatePlayerProfile();
                return;
            }

            newGame.showJoinRejectionMessage(toJoinRejectionMessage(decision));
            if (!newGame.askRetryJoin()) {
                throw new IllegalStateException("Ingreso a la sesion cancelado");
            }
        }
    }

    protected JoinDecision requestJoinAdmission(String proposedName) {
        return hostJoinHandler.handleJoin(new JoinRequest(proposedName));
    }

    private void applyAcceptedJoinDecision(JoinDecision decision) {
        this.playerId = decision.getPlayerId();
        this.userNamePlayer = decision.getDisplayName();
        this.newPlayer = new User(playerId, userNamePlayer);
    }

    private void loadOrCreatePlayerProfile() {
        try {
            Optional<User> existing = playerRepository.findByName(userNamePlayer);
            if (existing.isPresent()) {
                User profile = existing.get();
                newPlayer.setChips(profile.getChips());
            } else {
                playerRepository.save(newPlayer);
            }
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to load player profile: " + e.getMessage());
        }
    }

    private void savePlayerProfile() {
        try {
            playerRepository.save(newPlayer);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save player profile: " + e.getMessage());
        }
    }

    private String toJoinRejectionMessage(JoinDecision decision) {
        RejectReason reasonCode = decision.getReasonCode();
        if (reasonCode == RejectReason.NAME_TAKEN) {
            return "Ese nombre ya esta en uso en la sesion. Proba con otro.";
        }
        if (reasonCode == RejectReason.INVALID_FORMAT) {
            return "Nombre invalido. Usa solo letras (3 a 16 caracteres).";
        }
        return "No se pudo unir a la sesion por un error interno. Intenta nuevamente.";
    }

    private static HostJoinHandler createDefaultHostJoinHandler() {
        return new HostJoinHandler(new NameValidator(), new SessionNameRegistry());
    }

    public void viewUserChips() {
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    public String getPlayerId() {
        return playerId;
    }

    // =========================================================================
    //  FLUJO PRINCIPAL
    // =========================================================================

    public void createNewGame() {
        newGame.setGameActive(true);
        offerResume();

        while (newPlayer.getNumbChips() > 0) {
            playOneHand();

            // Preguntar si quiere seguir jugando
            boolean continuar = newGame.askPlayAgain(newPlayer.getNumbChips());
            if (!continuar) {
                newGame.requestGracefulShutdown();
                return;
            }
        }
        newGame.showGameOver(newPlayer.getNumbChips());
    }

    private void offerResume() {
        try {
            Optional<GameStateDto> latest = gameRepository.findLatest();
            if (latest.isPresent()) {
                GameStateDto state = latest.get();
                PlayerStateDto human = state.findHumanPlayer();
                if (human != null && human.getName().equals(userNamePlayer)) {
                    boolean resume = newGame.askResumeGame(human.getChips());
                    if (resume) {
                        newPlayer.setChips(human.getChips());
                    } else {
                        gameRepository.delete(state.getGameId());
                    }
                }
            }
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to check for saved game: " + e.getMessage());
        }
    }

    private void saveGameState(BettingRound.Phase phase) {
        if (pokerGame == null) return;
        try {
            GameStateDto state = buildGameStateDto(phase);
            gameRepository.save(state);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save game state: " + e.getMessage());
        }
    }

    private GameStateDto buildGameStateDto(BettingRound.Phase phase) {
        GameStateDto state = new GameStateDto();
        state.setGameId(userNamePlayer + "_latest");
        state.setPot(pokerGame.getPot());
        state.setCommunityCards(pokerGame.getCommunityCards());
        state.setRemainingDeck(pokerGame.getRemainingDeck());
        state.setDealerIndex(pokerGame.getDealerIndex());
        state.setCurrentPhase(phase);
        state.setPlayers(mapPlayers(pokerGame.getPlayers()));
        return state;
    }

    private List<PlayerStateDto> mapPlayers(List<Player> players) {
        List<PlayerStateDto> list = new ArrayList<>();
        for (Player p : players) {
            PlayerStateDto dto = new PlayerStateDto();
            dto.setPlayerId(p.getPlayerId());
            dto.setName(p.getName());
            dto.setHand(p.getHand());
            dto.setChips(p.getChips());
            dto.setCurrentBet(p.getCurrentBet());
            dto.setFolded(p.isFolded());
            dto.setAllIn(p.isAllIn());
            dto.setRole(p.getPlayerRole());
            dto.setAi(p instanceof AIPlayer);
            list.add(dto);
        }
        return list;
    }

    private void playOneHand() {
        newGame.clearTableForNewHand();
        this.pokerGame = new PokerGame(newPlayer);
        pokerGame.startNewRound();

        ArrayList<Card> manoJugador = pokerGame.getPlayerHand();
        newGame.showPlayerHand(manoJugador);
        newGame.awaitLastAnimation(newGame.getUiSyncTimeoutMs());
        newGame.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        newGame.showPot(pokerGame.getPot());

// ── PREFLOP ──────────────────────────────────────────────────────────
        runBettingPhase(BettingRound.Phase.PREFLOP);
        if (allFolded()) { saveHandEnd(); return; }

// ── FLOP ─────────────────────────────────────────────────────────────
        pokerGame.dealFlop();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 3);
        newGame.awaitLastAnimation(newGame.getUiSyncTimeoutMs());
        runBettingPhase(BettingRound.Phase.FLOP);
        if (allFolded()) { saveHandEnd(); return; }

// ── TURN ─────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 1);
        newGame.awaitLastAnimation(newGame.getUiSyncTimeoutMs());
        runBettingPhase(BettingRound.Phase.TURN);
        if (allFolded()) { saveHandEnd(); return; }

// ── RIVER ────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 1);
        newGame.awaitLastAnimation(newGame.getUiSyncTimeoutMs());
        runBettingPhase(BettingRound.Phase.RIVER);

        // ── SHOWDOWN ─────────────────────────────────────────────────────────
        endRound();
        saveHandEnd();
    }

    private void saveHandEnd() {
        saveGameState(BettingRound.Phase.RIVER);
        savePlayerProfile();
    }

    // =========================================================================
    //  RONDA DE APUESTAS
    // =========================================================================

    private void runBettingPhase(BettingRound.Phase phase) {
        pokerGame.resetRoundBets();
        BettingRound round = pokerGame.createBettingRound(phase);

        boolean humanFolded = newPlayer.isFolded();
        String lastPhaseFingerprint = "";
        int repeatedFingerprintCount = 0;

        while (true) {
            humanFolded = newPlayer.isFolded();

            BettingRound.Action action = null;
            int humanAmount = 0;

            boolean shouldWaitForHumanAction = !pokerGame.isPlayerAllIn() && !humanFolded;
            if (shouldWaitForHumanAction) {
                action = newGame.waitForPlayerAction(round, pokerGame.getPlayerCurrentBet(), newGame.getUiSyncTimeoutMs());
                if (action == null) {
                    action = resolveTimeoutAction(round, pokerGame.getPlayerCurrentBet());
                    newGame.resolvePendingPlayerAction(action);
                }
                if (action == BettingRound.Action.BET) {
                    humanAmount = newGame.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                } else if (action == BettingRound.Action.CALL) {
                    humanAmount = round.callAmount(pokerGame.getPlayerCurrentBet());
                } else if (action == BettingRound.Action.RAISE) {
                    int minRaise = round.minRaiseAmount();
                    humanAmount = newGame.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                }
            }

            PokerGame.AIBettingResult result = pokerGame.runUnifiedBettingRound(round.getCurrentBet(), action, humanAmount);
            newGame.showAIActions(result.log);
            newGame.awaitLastAnimation(newGame.getUiSyncTimeoutMs());

            boolean wasHumanFolded = humanFolded;
            humanFolded = result.humanFolded;
            if (humanFolded && !wasHumanFolded) {
                newGame.showPlayerFolded();
            }

            // Sincronizar BettingRound con la apuesta más alta que hizo la IA
            if (result.highBet > round.getCurrentBet()) {
                round.forceCurrentBet(result.highBet);
            }

            newGame.showPot(pokerGame.getPot());
            newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());

            if (hasSingleActivePlayer()) {
                break;
            }

            if (isBettingSettled(round.getCurrentBet())) {
                break;
            }

            String currentPhaseFingerprint = buildPhaseFingerprint(phase, round.getCurrentBet());
            if (currentPhaseFingerprint.equals(lastPhaseFingerprint)) {
                repeatedFingerprintCount++;
            } else {
                lastPhaseFingerprint = currentPhaseFingerprint;
                repeatedFingerprintCount = 1;
            }

            int nonProgressThreshold = Math.max(2, pokerGame.getActionablePlayersCount() * 2);
            if (repeatedFingerprintCount >= nonProgressThreshold) {
                break;
            }
        }

        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    private BettingRound.Action resolveTimeoutAction(BettingRound round, int playerCurrentBet) {
        if (round.canCheck(playerCurrentBet)) {
            System.out.println("Timeout esperando acción del jugador: aplicando CHECK por defecto.");
            return BettingRound.Action.CHECK;
        }
        System.out.println("Timeout esperando acción del jugador: aplicando FOLD por defecto.");
        return BettingRound.Action.FOLD;
    }

    private void applyHumanAction(BettingRound.Action action, BettingRound round) {
        switch (action) {
            case CHECK  -> pokerGame.humanCheck();
            case BET    -> {
                int amount = newGame.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                pokerGame.humanBet(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case CALL   -> {
                int call = round.callAmount(pokerGame.getPlayerCurrentBet());
                pokerGame.humanCall(call);
            }
            case RAISE  -> {
                int minRaise = round.minRaiseAmount();
                int amount   = newGame.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                pokerGame.humanRaise(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case ALL_IN -> pokerGame.humanAllIn();
            default     -> {}
        }
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    /** True when only one non-folded player remains. */
    private boolean hasSingleActivePlayer() {
        return pokerGame.getRemainingActivePlayersCount() <= 1;
    }

    /** True when all non-folded players are settled for current high bet. */
    private boolean isBettingSettled(int highBet) {
        for (Player current : pokerGame.getPlayers()) {
            if (current.isFolded()) {
                continue;
            }
            if (current.isAllIn()) {
                continue;
            }
            if (current.getCurrentBet() < highBet) {
                return false;
            }
        }
        return true;
    }

    private String buildPhaseFingerprint(BettingRound.Phase phase, int highBet) {
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(phase)
            .append("|")
            .append(highBet)
            .append("|")
            .append(pokerGame.getPot());

        for (Player current : pokerGame.getPlayers()) {
            fingerprint.append("|")
                .append(current.getPlayerId())
                .append(":")
                .append(current.isFolded())
                .append(":")
                .append(current.isAllIn())
                .append(":")
                .append(current.getCurrentBet())
                .append(":")
                .append(current.getChips());
        }

        return fingerprint.toString();
    }

    // =========================================================================
    //  FIN DE RONDA
    // =========================================================================

    private void endRound() {
        ShowdownResult showdownResult = pokerGame.determineShowdownResult();
        int pot = pokerGame.getPot();

        pokerGame.awardPot(showdownResult);

        newGame.showResult(
            pokerGame.getCommunityCards(),
            pokerGame.getPlayerHand(),
            showdownResult,
            pot
        );

        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    /** Si todas las IAs se retiraron, el humano gana el bote inmediatamente. */
    private boolean allFolded() {
        long active = pokerGame.getAIPlayers().stream().filter(ai -> !ai.isFolded()).count();
        if (active == 0) {
            pokerGame.awardPotToPlayer();
            newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
            newGame.showPot(0);
            return true;
        }
        return false;
    }
}
