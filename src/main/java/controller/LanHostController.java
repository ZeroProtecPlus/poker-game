package controller;

import javafx.application.Platform;

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
import network.host.ConnectedClient;
import network.host.HostServer;
import network.host.HostSession;
import network.host.PendingActionRegistry;
import network.protocol.ActionPayloads;
import network.protocol.GameStateJsonMapper;
import network.protocol.LanConstants;
import network.protocol.LanEnvelope;
import network.protocol.LanMessageType;
import org.json.JSONObject;
import view.LanDialogs;
import view.fx.GameTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

public class LanHostController {

    private final HostSession session;
    private final HostServer server;
    private final PendingActionRegistry pendingActions = new PendingActionRegistry();
    private final PlayerRepository playerRepository;
    private final GameRepository gameRepository;
    private final MachineIdProvider machineIdProvider;

    private User hostUser;
    private PokerGame pokerGame;
    private long stateSeq;
    private volatile String activePlayerId;
    private volatile BettingRound.Phase currentPhase;

    /** JavaFX GameTable used for lobby AND game rendering (replaces Swing GameView). */
    private GameTable table;

    private volatile boolean exitRequested;

    /** Accumulated action log entries for the current betting phase. */
    private List<String> recentActions = new ArrayList<>();

    public LanHostController(int port) throws IOException {
        this.session = new HostSession();
        this.server = new HostServer(port, session, this::onPlayerAction);
        this.machineIdProvider = FileMachineIdProvider.INSTANCE;

        DatabaseBootstrapper bootstrapper = new DatabaseBootstrapper();
        try {
            bootstrapper.bootstrap();
        } catch (RepositoryException e) {
            throw new IllegalStateException("Failed to bootstrap database", e);
        }
        this.playerRepository = new SqlitePlayerRepository(bootstrapper.getConnectionFactory());
        this.gameRepository = new SqliteGameRepository(bootstrapper.getConnectionFactory());

        session.setPendingActionRegistry(pendingActions);
        session.addDisconnectListener(this::onRemoteDisconnect);
        session.addLobbyListener(this::refreshHostLobbyHint);
        server.start();
    }

    public HostSession getSession() {
        return session;
    }

    public int getPort() {
        return server.getPort();
    }

    public void run() {
        table = GameTable.create();
        table.awaitUiReady(5000);
        table.setOnExitConfirmed(() -> exitRequested = true);

        registerHost();
        table.show();
        table.setStatusConnected(true);
        table.setStatusPhase("Lobby");

        try {
            runLobby();
            try {
                session.markGameStarted();
                runLanGame();
            } catch (IOException ex) {
                throw new IllegalStateException("Error de red en partida LAN: " + ex.getMessage(), ex);
            }
        } finally {
            table.requestGracefulShutdown();
            server.close();
            // No Platform.exit() — let the thread return and JVM exit naturally
        }
    }

    private void registerHost() {
        while (true) {
            String name = LanDialogs.showHostNameDialog();
            if (name == null || name.isBlank()) {
                throw new IllegalStateException("Nombre de host cancelado");
            }
            JoinDecision decision = session.registerHostPlayer(name);
            if (decision.isAccepted()) {
                hostUser = loadOrCreateProfile(
                    new User(decision.getPlayerId(), decision.getDisplayName())
                );
                table.showUserChipsSync(hostUser.getName(), hostUser.getNumbChips(), false);
                return;
            }
            LanDialogs.showJoinRejection(decision);
            if (!LanDialogs.askRetryJoin()) {
                throw new IllegalStateException("Registro de host cancelado");
            }
        }
    }

    private void runLobby() {
        // Show the player name in the GameTable already created in run()
        table.showLanLobby(hostUser.getName(), hostUser.getNumbChips());

        // Wait until we have MAX_HUMAN_PLAYERS total (host + remotes)
        while (session.lobbyPlayers().size() < LanConstants.MAX_HUMAN_PLAYERS && !exitRequested) {
            List<GameTable.LanSeatInfo> seats = new ArrayList<>();
            for (ConnectedClient client : session.getClients()) {
                seats.add(new GameTable.LanSeatInfo(client.getDisplayName(), hostUser.getNumbChips()));
            }
            table.updateLanLobbySeats(seats);

            // Show player count in the status area
            int currentCount = session.lobbyPlayers().size();
            Platform.runLater(() -> {
                table.setPotMessage("Jugadores: " + currentCount + "/" + LanConstants.MAX_HUMAN_PLAYERS);
            });

            // Broadcast updated lobby state to all connected clients
            // so they see the current player list in their GameTable seats.
            try {
                session.broadcastLobby();
            } catch (IOException broadcastEx) {
                System.err.println("Failed to broadcast lobby state: " + broadcastEx.getMessage());
            }

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (exitRequested) {
            pendingActions.cancelAll();
            table.hideFrame();
            throw new IllegalStateException("Juego cancelado");
        }

        // Update seats one final time to show all players
        List<GameTable.LanSeatInfo> finalSeats = new ArrayList<>();
        for (ConnectedClient client : session.getClients()) {
            finalSeats.add(new GameTable.LanSeatInfo(client.getDisplayName(), hostUser.getNumbChips()));
        }
        table.updateLanLobbySeats(finalSeats);

        // Final player count message
        Platform.runLater(() -> {
            table.setPotMessage("Jugadores: " + LanConstants.MAX_HUMAN_PLAYERS + "/" + LanConstants.MAX_HUMAN_PLAYERS);
        });

        // Brief pause so players see the full table before game starts
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Transition to game: clear table without hiding the GameTable.
        // GameTable stays visible and game rendering drives all UI.
        table.clearTable();
    }

    private void refreshHostLobbyHint() {
        // Lobby refresh is now driven by the polling loop above; this listener
        // is reserved for future live push-based UI updates.
    }

    private void runLanGame() throws IOException {
        List<User> remotes = new ArrayList<>();
        for (HostSession.UserSeat seat : session.buildRemoteSeats()) {
            remotes.add(new User(seat.playerId(), seat.displayName()));
        }
        pokerGame = new PokerGame(hostUser, remotes, true);

        while (hostUser.getNumbChips() > 0 && !exitRequested) {
            playOneHand();
            if (exitRequested) {
                pendingActions.cancelAll();
                break;
            }
            boolean continuar = table.askPlayAgain(hostUser.getNumbChips());
            if (!continuar) {
                savePlayerProfile(hostUser);
                broadcastGameOver(hostUser.getNumbChips());
                table.requestGracefulShutdown();
                return;
            }
        }
        if (exitRequested) {
            savePlayerProfile(hostUser);
        } else {
            // Host bust — broadcast game over to all clients
            broadcastGameOver(hostUser.getNumbChips());
            table.showGameOver(hostUser.getNumbChips());
        }
    }

    private void broadcastGameOver(int finalChips) throws IOException {
        JSONObject payload = new JSONObject()
            .put("finalChips", finalChips)
            .put("winner", hostUser.getName());
        session.broadcast(new LanEnvelope(LanMessageType.GAME_OVER, payload));
    }

    private void playOneHand() throws IOException {
        table.clearTable();
        pokerGame.startNewRound();

        ArrayList<Card> hostHand = pokerGame.getPlayerHand();
        table.showPlayerHand(hostHand);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        table.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        table.updatePot(pokerGame.getPot());
        currentPhase = BettingRound.Phase.PREFLOP;
        table.setStatusPhase("PREFLOP");
        broadcastState(currentPhase);

        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealFlop();
        table.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 3);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.FLOP;
        table.setStatusPhase("FLOP");
        broadcastState(currentPhase);
        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealTurnOrRiver();
        table.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 1);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.TURN;
        table.setStatusPhase("TURN");
        broadcastState(currentPhase);
        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealTurnOrRiver();
        table.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 1);
        table.awaitLastAnimation(table.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.RIVER;
        table.setStatusPhase("RIVER");
        broadcastState(currentPhase);
        runLanBettingPhase(currentPhase);

        endRound();
        saveGameState(BettingRound.Phase.RIVER);
        savePlayerProfile(hostUser);
    }

    private void runLanBettingPhase(BettingRound.Phase phase) throws IOException {
        pokerGame.resetRoundBets();
        BettingRound round = pokerGame.createBettingRound(phase);

        String lastFingerprint = "";
        int repeated = 0;

        // Accumulated action log — keep entries across loop iterations so the
        // player sees the full betting sequence, not just the last batch.
        recentActions = new ArrayList<>();

        while (true) {
            Map<String, PokerGame.HumanActionEntry> actions = collectHumanActions(round, phase);

            PokerGame.AIBettingResult result = pokerGame.runLanUnifiedBettingRound(
                round.getCurrentBet(),
                phase,
                actions
            );
            recentActions.addAll(result.log);
            table.setActionLog(recentActions);
            table.awaitLastAnimation(table.getUiSyncTimeoutMs());

            if (result.highBet > round.getCurrentBet()) {
                round.forceCurrentBet(result.highBet);
            }

            table.updatePot(pokerGame.getPot());
            table.showUserChips(hostUser.getName(), hostUser.getNumbChips());
            broadcastState(phase);

            if (pokerGame.getRemainingActivePlayersCount() <= 1) {
                break;
            }
            if (isBettingSettled(round.getCurrentBet())) {
                break;
            }

            String fingerprint = phase + "|" + round.getCurrentBet() + "|" + pokerGame.getPot();
            if (fingerprint.equals(lastFingerprint)) {
                repeated++;
            } else {
                lastFingerprint = fingerprint;
                repeated = 1;
            }
            if (repeated >= Math.max(2, pokerGame.getActionablePlayersCount() * 2)) {
                break;
            }
        }
        activePlayerId = null;
        broadcastState(phase);
    }

    private Map<String, PokerGame.HumanActionEntry> collectHumanActions(
        BettingRound round,
        BettingRound.Phase phase
    ) throws IOException {
        // Clear any stale pending actions from previous iterations so that
        // a late-arriving action from the last round is never consumed here.
        pendingActions.cancelAll();

        Map<String, PokerGame.HumanActionEntry> actions = new HashMap<>();

        for (User human : pokerGame.getAllHumanUsers()) {
            if (human.isFolded() || human.isAllIn()) {
                continue;
            }

            activePlayerId = human.getPlayerId();
            broadcastState(phase);

            BettingRound.Action action;
            int amount = 0;

            if (pokerGame.isLocalHuman(human)) {
                table.highlightActivePlayer(human.getPlayerId(), buildHostPlayerNameMap());
                table.setStatusTurn("Tu turno");
                int playerBet = human.getCurrentBet();
                action = table.awaitPlayerAction(round, playerBet, table.getUiSyncTimeoutMs());
                if (action == null) {
                    // Timeout: skip this player — don't fold, stay in hand
                    continue;
                }
                if (action == BettingRound.Action.FOLD) {
                    table.showPlayerFolded();
                }
                amount = resolveAmount(action, round, human, playerBet);
            } else {
                table.highlightActivePlayer(human.getPlayerId(), buildHostPlayerNameMap());
                table.setStatusTurn("Turno de: " + human.getName());
                if (session.findClient(human.getPlayerId()).isEmpty()) {
                    // Client disconnected — skip this player
                    continue;
                } else {
                    try {
                        PendingActionRegistry.RemoteAction remote = pendingActions.awaitAction(
                            human.getPlayerId(),
                            LanConstants.ACTION_TIMEOUT_MS
                        );
                        action = remote.action();
                        amount = remote.amount();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        // Skip this player — don't auto-fold
                        continue;
                    } catch (CancellationException ex) {
                        // Pending action was cancelled (e.g. on window close)
                        continue;
                    } catch (TimeoutException ex) {
                        // Skip this player — don't auto-fold, stay in hand
                        notifyActionRejected(human.getPlayerId(), "Tiempo agotado; turno saltado.");
                        continue;
                    }
                }
            }

            actions.put(human.getPlayerId(), new PokerGame.HumanActionEntry(action, amount));

            // Immediately reflect this player's bet on the round so the
            // next player's broadcast state shows the corrected high-bet.
            // Without this, subsequent players see CHECK instead of CALL
            // because the round still carries the initial (pre-action) high-bet.
            if (action == BettingRound.Action.BET || action == BettingRound.Action.RAISE) {
                int newTotal = human.getCurrentBet() + amount;
                if (newTotal > round.getCurrentBet()) {
                    round.forceCurrentBet(newTotal);
                }
            } else if (action == BettingRound.Action.ALL_IN) {
                // All-in puts all remaining chips in — use total chips as the new bet
                int allInTotal = human.getCurrentBet() + human.getNumbChips();
                if (allInTotal > round.getCurrentBet()) {
                    round.forceCurrentBet(allInTotal);
                }
            }
        }
        activePlayerId = null;
        table.setStatusTurn("");
        table.highlightActivePlayer(null, null);
        return actions;
    }

    private int resolveAmount(
        BettingRound.Action action,
        BettingRound round,
        User human,
        int playerCurrentBet
    ) {
        if (action == BettingRound.Action.BET) {
            return table.getPlayerBetAmount(round.getBigBlind(), human.getNumbChips());
        }
        if (action == BettingRound.Action.CALL) {
            return round.callAmount(playerCurrentBet);
        }
        if (action == BettingRound.Action.RAISE) {
            return table.getPlayerBetAmount(round.minRaiseAmount(), human.getNumbChips());
        }
        return 0;
    }

    /** Builds a playerId → displayName map for the host's active-player highlight. */
    private Map<String, String> buildHostPlayerNameMap() {
        Map<String, String> map = new HashMap<>();
        map.put(hostUser.getPlayerId(), hostUser.getName());
        for (User remote : pokerGame.getLanHumanPlayers()) {
            map.put(remote.getPlayerId(), remote.getName());
        }
        return map;
    }

    private void onPlayerAction(String connectionPlayerId, LanEnvelope envelope) throws IOException {
        JSONObject payload = envelope.getPayload();
        String claimedId = ActionPayloads.playerIdFromAction(payload);
        if (!connectionPlayerId.equals(claimedId)) {
            notifyActionRejected(connectionPlayerId, "playerId no coincide con la conexión.");
            return;
        }
        // Tolerate brief activePlayerId mismatch caused by thread scheduling:
        // if the registry already has a pending entry for this player, accept the
        // action even when the volatile activePlayerId has already advanced.
        if ((activePlayerId == null || !activePlayerId.equals(claimedId))
                && !pendingActions.hasPending(claimedId)) {
            notifyActionRejected(claimedId, "No es tu turno.");
            return;
        }
        BettingRound.Action action = ActionPayloads.actionFromJson(payload);
        int amount = ActionPayloads.amountFromJson(payload);
        boolean accepted = pendingActions.complete(
            claimedId,
            new PendingActionRegistry.RemoteAction(action, amount)
        );
        if (!accepted) {
            notifyActionRejected(claimedId, "No hay acción pendiente para este jugador.");
        }
    }

    private void notifyActionRejected(String playerId, String reason) throws IOException {
        Optional<ConnectedClient> client = session.findClient(playerId);
        if (client.isPresent()) {
            client.get().send(new LanEnvelope(
                LanMessageType.ACTION_REJECTED,
                ActionPayloads.actionRejected(reason)
            ));
        }
    }

    private BettingRound.Action resolveTimeoutAction(BettingRound round, int playerCurrentBet) {
        if (round.canCheck(playerCurrentBet)) {
            return BettingRound.Action.CHECK;
        }
        return BettingRound.Action.FOLD;
    }

    private boolean isBettingSettled(int highBet) {
        for (Player current : pokerGame.getPlayers()) {
            if (current.isFolded() || current.isAllIn()) {
                continue;
            }
            if (current.getCurrentBet() < highBet) {
                return false;
            }
        }
        return true;
    }

    private boolean allOthersFolded() throws IOException {
        long activeAi = pokerGame.getAIPlayers().stream().filter(ai -> !ai.isFolded()).count();
        long activeRemote = pokerGame.getLanHumanPlayers().stream().filter(u -> !u.isFolded()).count();
        if (activeAi == 0 && activeRemote == 0) {
            pokerGame.awardPotToPlayer();
            table.showUserChips(hostUser.getName(), hostUser.getNumbChips());
            table.updatePot(0);
            broadcastState(BettingRound.Phase.RIVER);
            return true;
        }
        return false;
    }

    private void endRound() throws IOException {
        ShowdownResult result = pokerGame.determineShowdownResult();
        int pot = pokerGame.getPot();
        pokerGame.awardPot(result);
        showResult(result, pot);
        table.showUserChips(hostUser.getName(), hostUser.getNumbChips());
        broadcastState(BettingRound.Phase.RIVER);
        session.broadcast(new LanEnvelope(
            LanMessageType.HAND_RESULT,
            new JSONObject()
                .put("winner", result.getWinners().isEmpty() ? "" : result.getWinners().get(0).getName())
                .put("pot", pot)
        ));
    }

    /**
     * Shows the showdown result on the GameTable.
     * Translates the {@link ShowdownResult} into the GameTable's result banner.
     */
    private void showResult(ShowdownResult result, int pot) {
        if (result == null || result.getWinners().isEmpty()) {
            table.showResult("Sin ganador definido", false);
            return;
        }
        if (result.isTie()) {
            StringBuilder names = new StringBuilder();
            for (Player w : result.getWinners()) {
                if (names.length() > 0) names.append(", ");
                names.append(w.getName());
            }
            table.showResult("Empate: " + names + " dividen " + String.format("%,d", pot), false);
            return;
        }
        Player winner = result.getWinners().get(0);
        boolean humanWon = winner instanceof User;
        String handName = result.getBestRank() != null ? result.getBestRank().spanishName : "";
        if (humanWon) {
            table.showResult("¡Ganaste! +" + String.format("%,d", pot) + "  |  " + handName, true);
        } else {
            table.showResult(winner.getName() + " gana " + String.format("%,d", pot) + "  |  " + handName, false);
        }
    }

    /**
     * Called from the network thread when a remote client disconnects mid-game.
     * Broadcasts updated game state so remaining clients immediately see the removal.
     */
    private void onRemoteDisconnect() {
        if (pokerGame == null || currentPhase == null) {
            return;
        }
        try {
            broadcastState(currentPhase);
        } catch (IOException ex) {
            System.err.println("Failed to broadcast state on disconnect: " + ex.getMessage());
        }
    }

    private void broadcastState(BettingRound.Phase phase) throws IOException {
        GameStateDto state = buildGameStateDto(phase);
        stateSeq++;
        JSONObject stateJson = GameStateJsonMapper.toJson(state);

        for (ConnectedClient client : session.getClients()) {
            GameStateDto masked = maskHands(state, client.getPlayerId());
            client.send(new LanEnvelope(
                LanMessageType.GAME_STATE,
                ActionPayloads.gameState(
                    stateSeq,
                    client.getPlayerId(),
                    GameStateJsonMapper.toJson(masked),
                    activePlayerId
                )
            ));
        }
    }

    private GameStateDto maskHands(GameStateDto state, String viewerId) {
        GameStateDto copy = copyState(state);
        for (PlayerStateDto player : copy.getPlayers()) {
            if (!viewerId.equals(player.getPlayerId())) {
                player.setHand(new ArrayList<>());
            }
        }
        return copy;
    }

    private GameStateDto copyState(GameStateDto source) {
        GameStateDto copy = new GameStateDto();
        copy.setGameId(source.getGameId());
        copy.setMachineId(source.getMachineId());
        copy.setHumanChips(source.getHumanChips());
        copy.setPot(source.getPot());
        copy.setCommunityCards(new ArrayList<>(source.getCommunityCards()));
        copy.setRemainingDeck(new ArrayList<>(source.getRemainingDeck()));
        copy.setDealerIndex(source.getDealerIndex());
        copy.setCurrentPhase(source.getCurrentPhase());
        copy.setTimestamp(source.getTimestamp());
        copy.setRecentActions(new ArrayList<>(source.getRecentActions()));
        List<PlayerStateDto> players = new ArrayList<>();
        for (PlayerStateDto p : source.getPlayers()) {
            PlayerStateDto clone = new PlayerStateDto();
            clone.setPlayerId(p.getPlayerId());
            clone.setName(p.getName());
            clone.setHand(new ArrayList<>(p.getHand()));
            clone.setChips(p.getChips());
            clone.setCurrentBet(p.getCurrentBet());
            clone.setFolded(p.isFolded());
            clone.setAllIn(p.isAllIn());
            clone.setRole(p.getRole());
            clone.setAi(p.isAi());
            players.add(clone);
        }
        copy.setPlayers(players);
        return copy;
    }

    private GameStateDto buildGameStateDto(BettingRound.Phase phase) {
        GameStateDto state = new GameStateDto();
        state.setMachineId(machineIdProvider.getMachineId());
        state.setGameId(session.getSessionId());
        state.setPot(pokerGame.getPot());
        state.setCommunityCards(pokerGame.getCommunityCards());
        state.setRemainingDeck(pokerGame.getRemainingDeck());
        state.setDealerIndex(pokerGame.getDealerIndex());
        state.setCurrentPhase(phase);
        state.setRecentActions(new ArrayList<>(recentActions));
        int totalHumanChips = hostUser.getNumbChips();
        for (User remote : pokerGame.getLanHumanPlayers()) {
            totalHumanChips += remote.getNumbChips();
        }
        state.setHumanChips(totalHumanChips);
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

    private void saveGameState(BettingRound.Phase phase) {
        try {
            gameRepository.saveByMachineId(machineIdProvider.getMachineId(), buildGameStateDto(phase));
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save LAN game state: " + e.getMessage());
        }
    }

    private User loadOrCreateProfile(User sessionUser) {
        try {
            Optional<User> existing = playerRepository.findByName(sessionUser.getName());
            if (existing.isPresent()) {
                User profile = existing.get();
                User linked = new User(profile.getPlayerId(), profile.getName());
                linked.setChips(profile.getChips());
                return linked;
            }
            playerRepository.save(sessionUser);
            return sessionUser;
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to load profile: " + rootCauseMessage(e));
            return sessionUser;
        }
    }

    private void savePlayerProfile(User user) {
        try {
            playerRepository.save(user);
        } catch (RepositoryException e) {
            System.err.println("Warning: failed to save profile: " + rootCauseMessage(e));
        }
    }

    private static String rootCauseMessage(RepositoryException e) {
        Throwable cause = e.getCause();
        return cause != null && cause.getMessage() != null ? cause.getMessage() : e.getMessage();
    }
}
