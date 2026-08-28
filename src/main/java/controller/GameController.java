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
import view.LanDialogs;
import view.fx.GameTable;
import view.fx.JavaFxBootstrap;
import view.fx.PlayerNameDialog;
import view.fx.ResumeGameDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GameController {

    private PokerGame pokerGame;
    private String playerId;
    private String userNamePlayer;
    private User newPlayer;
    private GameTable table;
    private volatile boolean exitRequested;
    private final HostJoinHandler hostJoinHandler;
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final MachineIdProvider machineIdProvider;

    public GameController() {
        this(createDefaultHostJoinHandler(), createDefaultRepositories());
    }

    protected GameController(HostJoinHandler hostJoinHandler, Repositories repositories) {
        this(hostJoinHandler, repositories, FileMachineIdProvider.INSTANCE);
    }

    protected GameController(HostJoinHandler hostJoinHandler, Repositories repositories, MachineIdProvider machineIdProvider) {
        this.hostJoinHandler = hostJoinHandler;
        this.playerRepository = repositories.playerRepository();
        this.gameRepository = repositories.gameRepository();
        this.machineIdProvider = machineIdProvider;
    }

    private static Repositories createDefaultRepositories() {
        // Bootstrap del storage antes de construir repositorios para garantizar esquema listo.
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

    // ── BUG 4 FIX: GameTable only shown AFTER name is accepted ────────────────

    /**
     * Prompts the player for their name and validates it.
     *
     * @return {@code true} if the player was successfully created,
     *         {@code false} if the user cancelled (should return to menu)
     */
    public boolean createNewPlayer() {
        // Create GameTable but do NOT show it yet — the empty table should not
        // render behind the PlayerNameDialog.  The stage is built (awaitUiReady
        // ensures the FX scene graph exists) but remains hidden until the name
        // is accepted.
        JavaFxBootstrap.ensureStarted();
        table = GameTable.create();
        table.awaitUiReady(5000);
        // table.show() moved AFTER name acceptance below

        // Loop de admisión: valida nombre en host y evita arrancar sin sesión válida.
        while (true) {
            String name = PlayerNameDialog.showAndWaitBlocking();
            if (name == null || name.trim().isEmpty()) {
                table.requestGracefulShutdown();
                return false; // user cancelled — return to menu
            }
            JoinDecision decision = requestJoinAdmission(name.trim());
            if (decision.isAccepted()) {
                applyAcceptedJoinDecision(decision);
                loadOrCreatePlayerProfile();
                // NOW show the table — name and profile are confirmed
                table.show();
                table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
                return true;
            }

            LanDialogs.showJoinRejection(decision, table.getStage());
            if (!LanDialogs.askRetryJoin(table.getStage())) {
                table.requestGracefulShutdown();
                return false; // user cancelled retry — return to menu
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
                // Reutilizar el player_id persistido: cada sesión genera un UUID nuevo en join,
                // pero la tabla players tiene UNIQUE(name); insertar con id distinto falla.
                this.playerId = profile.getPlayerId();
                this.newPlayer = new User(profile.getPlayerId(), profile.getName());
                newPlayer.setChips(profile.getChips());
            } else {
                playerRepository.save(newPlayer);
            }
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to load player profile: " + rootCauseMessage(e));
        }
    }

    private void savePlayerProfile() {
        try {
            playerRepository.save(newPlayer);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save player profile: " + rootCauseMessage(e));
        }
    }

    private static String rootCauseMessage(RepositoryException e) {
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
    }

    private static HostJoinHandler createDefaultHostJoinHandler() {
        return new HostJoinHandler(new NameValidator(), new SessionNameRegistry());
    }

    public void viewUserChips() {
        table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    public String getPlayerId() {
        return playerId;
    }

    // =========================================================================
    //  FLUJO PRINCIPAL
    // =========================================================================

    public void createNewGame() {
        // GameTable is already showing (shown in createNewPlayer after name accepted).
        table.setGameActive(true);
        table.setOnExitConfirmed(() -> exitRequested = true);
        offerResume();

        while (newPlayer.getNumbChips() > 0) {
            if (exitRequested) {
                break;
            }
            playOneHand();

            if (exitRequested) {
                break;
            }

            // ── fix-endgame-states: elimination / game-over checks ──────────
            // Check if the human was eliminated during this hand
            if (newPlayer.isEliminated()) {
                break;
            }
            // Check if all opponents have 0 chips (human wins)
            if (pokerGame.areAllOpponentsEliminated()) {
                break;
            }

            // Preguntar si quiere seguir jugando
            boolean continuar = table.askPlayAgain(newPlayer.getNumbChips());
            if (!continuar) {
                saveHandEnd();
                table.requestGracefulShutdown();
                return;
            }
        }
        tryDeleteGameState();

        if (exitRequested) {
            return;
        }

        // ── fix-endgame-states: determine winner name for game-over display ──
        String winnerName;
        if (newPlayer.getNumbChips() > 0) {
            winnerName = newPlayer.getName();
        } else {
            Player winner = pokerGame.determineOverallWinner();
            winnerName = winner != null ? winner.getName() : null;
        }
        table.showGameOver(winnerName, newPlayer.getNumbChips());
    }

    private void offerResume() {
        // Recupera estado persistido por máquina para permitir reanudar sin elegir perfil manual.
        try {
            String machineId = machineIdProvider.getMachineId();
            Optional<GameStateDto> savedGame = gameRepository.loadByMachineId(machineId);
            if (savedGame.isPresent()) {
                GameStateDto state = savedGame.get();
                boolean resume = ResumeGameDialog.showAndWaitBlocking(state.getHumanChips()).join();
                if (resume) {
                    newPlayer.setChips(state.getHumanChips());
                    table.showUserChipsSync(userNamePlayer, newPlayer.getNumbChips(), true);
                    return;
                } else {
                    gameRepository.deleteByMachineId(machineId);
                }
            }
            // No save or declined resume — show chips with animation
            table.showUserChipsSync(userNamePlayer, newPlayer.getNumbChips(), false);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to check for saved game: " + e.getMessage());
            table.showUserChipsSync(userNamePlayer, newPlayer.getNumbChips(), false);
        }
    }

    private void saveGameState(BettingRound.Phase phase) {
        if (pokerGame == null) return;
        try {
            // Persistimos snapshot atómico del estado para reanudar la partida.
            GameStateDto state = buildGameStateDto(phase);
            String machineId = machineIdProvider.getMachineId();
            gameRepository.saveByMachineId(machineId, state);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save game state: " + e.getMessage());
        }
    }

    private void tryDeleteGameState() {
        try {
            String machineId = machineIdProvider.getMachineId();
            gameRepository.deleteByMachineId(machineId);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to delete game state on bust: " + e.getMessage());
        }
    }

    private GameStateDto buildGameStateDto(BettingRound.Phase phase) {
        GameStateDto state = new GameStateDto();
        String machineId = machineIdProvider.getMachineId();
        state.setMachineId(machineId);
        state.setGameId(userNamePlayer + "_latest");
        state.setPot(pokerGame.getPot());
        state.setCommunityCards(pokerGame.getCommunityCards());
        state.setRemainingDeck(pokerGame.getRemainingDeck());
        state.setDealerIndex(pokerGame.getDealerIndex());
        state.setCurrentPhase(phase);
        state.setPlayers(mapPlayers(pokerGame.getPlayers()));
        Player human = findHumanPlayer();
        if (human != null) {
            state.setHumanChips(human.getChips());
        }
        return state;
    }

    private Player findHumanPlayer() {
        for (Player p : pokerGame.getPlayers()) {
            if (!(p instanceof AIPlayer)) {
                return p;
            }
        }
        return null;
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

    // ── BUG 3c FIX: add setStatusPhase before each betting phase ──────────────

    private void playOneHand() {
        // Mano completa: inicializa mesa, ejecuta fases y termina en showdown o fold.
        if (exitRequested) {
            return;
        }
        table.clearTable();
        this.pokerGame = new PokerGame(newPlayer);
        pokerGame.startNewRound();

        ArrayList<Card> manoJugador = pokerGame.getPlayerHand();
        table.showPlayerHand(manoJugador);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        table.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        table.updatePot(pokerGame.getPot());

// ── PREFLOP ──────────────────────────────────────────────────────────
        table.setStatusPhase("PREFLOP");
        runBettingPhase(BettingRound.Phase.PREFLOP);
        if (exitRequested) { saveHandEnd(); return; }
        if (allFolded()) { saveHandEnd(); return; }

// ── FLOP ─────────────────────────────────────────────────────────────
        pokerGame.dealFlop();
        table.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 3);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        table.setStatusPhase("FLOP");
        runBettingPhase(BettingRound.Phase.FLOP);
        if (exitRequested) { saveHandEnd(); return; }
        if (allFolded()) { saveHandEnd(); return; }

// ── TURN ─────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        table.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 1);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        table.setStatusPhase("TURN");
        runBettingPhase(BettingRound.Phase.TURN);
        if (exitRequested) { saveHandEnd(); return; }
        if (allFolded()) { saveHandEnd(); return; }

// ── RIVER ────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        table.showCommunityCards(pokerGame.getCommunityCards(), manoJugador, 1);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        table.setStatusPhase("RIVER");
        runBettingPhase(BettingRound.Phase.RIVER);
        if (exitRequested) { saveHandEnd(); return; }

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

    // ── BUG 3a FIX: accumulate action log across loop iterations ──────────────
    // ── BUG 3c FIX: show "Tu turno" before waiting for human action ──────────

    private void runBettingPhase(BettingRound.Phase phase) {
        // Fase de apuestas unificada: humano + IA sincronizados con el estado del bote.
        pokerGame.resetRoundBets();
        BettingRound round = pokerGame.createBettingRound(phase);

        boolean humanFolded = newPlayer.isFolded();
        String lastPhaseFingerprint = "";
        int repeatedFingerprintCount = 0;

        // Accumulated action log — keep entries across loop iterations so the
        // player sees the full betting sequence, not just the last batch.
        List<String> accumulatedLog = new ArrayList<>();

        while (true) {
            if (exitRequested) {
                break;
            }
            humanFolded = newPlayer.isFolded();

            BettingRound.Action action = null;
            int humanAmount = 0;

            boolean shouldWaitForHumanAction = !pokerGame.isPlayerAllIn() && !humanFolded;
            if (shouldWaitForHumanAction) {
                table.setStatusTurn("Tu turno");
                action = table.awaitPlayerAction(round, pokerGame.getPlayerCurrentBet(), table.getUiSyncTimeoutMs());
                table.setStatusTurn("");
                if (action == null) {
                    // Timeout: skip this player's turn — don't fold, stay in hand
                    action = null;
                }
                if (action == BettingRound.Action.BET) {
                    humanAmount = table.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                } else if (action == BettingRound.Action.CALL) {
                    humanAmount = round.callAmount(pokerGame.getPlayerCurrentBet());
                } else if (action == BettingRound.Action.RAISE) {
                    int minRaise = round.minRaiseAmount();
                    humanAmount = table.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                }
            }

            PokerGame.AIBettingResult result = pokerGame.runUnifiedBettingRound(round.getCurrentBet(), action, humanAmount, phase);
            accumulatedLog.addAll(result.log);
            table.setActionLog(accumulatedLog);

            // Refresh AI badges so fold state changes are immediately visible
            List<AIPlayer> aiPlayers = pokerGame.getAIPlayers();
            table.updateAIPlayers(aiPlayers);

            // Simulate human-like thinking delay when AIs are still active
            long activeAICount = aiPlayers.stream().filter(ai -> !ai.isFolded()).count();
            if (activeAICount > 0) {
                try {
                    Thread.sleep(800 + (long)(Math.random() * 700)); // 0.8–1.5s
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            table.awaitLastAnimation(table.getUiSyncTimeoutMs());

            boolean wasHumanFolded = humanFolded;
            humanFolded = result.humanFolded;
            if (humanFolded && !wasHumanFolded) {
                table.showPlayerFolded();
            }

            // Sincronizar la ronda con el máximo de apuesta de IA para mantener coherencia.
            if (result.highBet > round.getCurrentBet()) {
                round.forceCurrentBet(result.highBet);
            }

            table.updatePot(pokerGame.getPot());
            table.showUserChips(userNamePlayer, newPlayer.getNumbChips());

            if (hasSingleActivePlayer()) {
                break;
            }

            if (isBettingSettled(round.getCurrentBet())) {
                break;
            }

            // Fingerprint de estado para cortar loops sin progreso real.
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

        table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
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
                int amount = table.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                pokerGame.humanBet(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case CALL   -> {
                int call = round.callAmount(pokerGame.getPlayerCurrentBet());
                pokerGame.humanCall(call);
            }
            case RAISE  -> {
                int minRaise = round.minRaiseAmount();
                int amount   = table.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                pokerGame.humanRaise(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case ALL_IN -> pokerGame.humanAllIn();
            default     -> {}
        }
        table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
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

        // Build result text matching the old Swing TablePanel format
        String resultText;
        boolean isWin;
        if (showdownResult == null || showdownResult.getWinners().isEmpty()) {
            resultText = "Sin ganador definido";
            isWin = false;
        } else if (showdownResult.isTie()) {
            String names = showdownResult.getWinners().stream()
                .map(Player::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
            resultText = "Empate: " + names + " dividen " + String.format("%,d", pot)
                + "  |  " + showdownResult.getBestRank().spanishName;
            isWin = false;
        } else {
            Player winner = showdownResult.getWinners().get(0);
            if (winner instanceof User) {
                resultText = "¡Ganaste! +" + String.format("%,d", pot)
                    + "  |  " + showdownResult.getBestRank().spanishName;
                isWin = true;
            } else {
                resultText = winner.getName() + " gana el bote de " + String.format("%,d", pot)
                    + "  |  Tu mano: " + showdownResult.getBestRank().spanishName;
                isWin = false;
            }
        }

        table.showResult(resultText, isWin);
        table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    /** Si todas las IAs se retiraron, el humano gana el bote inmediatamente. */
    private boolean allFolded() {
        long active = pokerGame.getAIPlayers().stream().filter(ai -> !ai.isFolded()).count();
        if (active == 0) {
            pokerGame.awardPotToPlayer();
            table.showUserChips(userNamePlayer, newPlayer.getNumbChips());
            table.updatePot(0);
            return true;
        }
        return false;
    }
}
