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
import view.GameView;
import view.LanDialogs;
import view.fx.GameTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class LanHostController {

    private final GameView view;
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

    public LanHostController(GameView view, int port) throws IOException {
        this.view = view;
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
        view.awaitUiReady(view.getUiSyncTimeoutMs());
        registerHost();
        runLobby();
        try {
            session.markGameStarted();
            runLanGame();
        } catch (IOException ex) {
            throw new IllegalStateException("Error de red en partida LAN: " + ex.getMessage(), ex);
        } finally {
            server.close();
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
                view.showUserChipsSync(hostUser.getName(), hostUser.getNumbChips(), false);
                return;
            }
            LanDialogs.showJoinRejection(decision);
            if (!LanDialogs.askRetryJoin()) {
                throw new IllegalStateException("Registro de host cancelado");
            }
        }
    }

    private void runLobby() {
        // Create and show the GameTable for lobby display
        GameTable lobby = GameTable.create();
        lobby.awaitUiReady(5000);
        lobby.show();
        lobby.showLanLobby(hostUser.getName(), hostUser.getNumbChips());

        // Show connection info in the action log area via pot label
        Platform.runLater(() -> {
            lobby.updatePot(0);
        });

        // Wait until we have MAX_HUMAN_PLAYERS total (host + remotes)
        while (session.lobbyPlayers().size() < LanConstants.MAX_HUMAN_PLAYERS) {
            List<GameTable.LanSeatInfo> seats = new ArrayList<>();
            for (ConnectedClient client : session.getClients()) {
                seats.add(new GameTable.LanSeatInfo(client.getDisplayName(), 10000));
            }
            lobby.updateLanLobbySeats(seats);

            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Update seats one final time to show all players
        List<GameTable.LanSeatInfo> finalSeats = new ArrayList<>();
        for (ConnectedClient client : session.getClients()) {
            finalSeats.add(new GameTable.LanSeatInfo(client.getDisplayName(), 10000));
        }
        lobby.updateLanLobbySeats(finalSeats);

        // Brief pause so players see the full table before game starts
        try {
            Thread.sleep(800);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Clean up lobby display
        lobby.hideLanLobby();
    }

    private void refreshHostLobbyHint() {
        // Lobby refresh is now driven by the polling loop above; this listener
        // is reserved for future live push-based UI updates.
    }

    private void runLanGame() throws IOException {
        view.setGameActive(true);
        List<User> remotes = new ArrayList<>();
        for (HostSession.UserSeat seat : session.buildRemoteSeats()) {
            remotes.add(new User(seat.playerId(), seat.displayName()));
        }
        pokerGame = new PokerGame(hostUser, remotes, true);

        while (hostUser.getNumbChips() > 0) {
            playOneHand();
            boolean continuar = view.askPlayAgain(hostUser.getNumbChips());
            if (!continuar) {
                savePlayerProfile(hostUser);
                view.requestGracefulShutdown();
                return;
            }
        }
        view.showGameOver(hostUser.getNumbChips());
    }

    private void playOneHand() throws IOException {
        view.clearTableForNewHand();
        pokerGame.startNewRound();

        ArrayList<Card> hostHand = pokerGame.getPlayerHand();
        view.showPlayerHand(hostHand);
        view.awaitLastAnimation(view.getUiSyncTimeoutMs());
        view.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        view.showPot(pokerGame.getPot());
        currentPhase = BettingRound.Phase.PREFLOP;
        broadcastState(currentPhase);

        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealFlop();
        view.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 3);
        view.awaitLastAnimation(view.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.FLOP;
        broadcastState(currentPhase);
        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealTurnOrRiver();
        view.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 1);
        view.awaitLastAnimation(view.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.TURN;
        broadcastState(currentPhase);
        runLanBettingPhase(currentPhase);
        if (allOthersFolded()) {
            return;
        }

        pokerGame.dealTurnOrRiver();
        view.showCommunityCards(pokerGame.getCommunityCards(), hostHand, 1);
        view.awaitLastAnimation(view.getUiSyncTimeoutMs());
        currentPhase = BettingRound.Phase.RIVER;
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

        while (true) {
            Map<String, PokerGame.HumanActionEntry> actions = collectHumanActions(round, phase);

            PokerGame.AIBettingResult result = pokerGame.runLanUnifiedBettingRound(
                round.getCurrentBet(),
                phase,
                actions
            );
            view.showAIActions(result.log);
            view.awaitLastAnimation(view.getUiSyncTimeoutMs());

            if (result.highBet > round.getCurrentBet()) {
                round.forceCurrentBet(result.highBet);
            }

            view.showPot(pokerGame.getPot());
            view.showUserChips(hostUser.getName(), hostUser.getNumbChips());
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
                int playerBet = human.getCurrentBet();
                action = view.waitForPlayerAction(round, playerBet, view.getUiSyncTimeoutMs());
                if (action == null) {
                    action = resolveTimeoutAction(round, playerBet);
                    view.resolvePendingPlayerAction(action);
                }
                amount = resolveAmount(action, round, human, playerBet);
            } else {
                // Check if the client is still connected before waiting.
                // If disconnected, immediately FOLD without the 3-second timeout.
                if (session.findClient(human.getPlayerId()).isEmpty()) {
                    action = resolveTimeoutAction(round, human.getCurrentBet());
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
                        action = BettingRound.Action.FOLD;
                    } catch (TimeoutException ex) {
                        action = resolveTimeoutAction(round, human.getCurrentBet());
                        notifyActionRejected(human.getPlayerId(), "Tiempo agotado; fold automático.");
                    }
                }
            }

            actions.put(human.getPlayerId(), new PokerGame.HumanActionEntry(action, amount));
        }
        activePlayerId = null;
        return actions;
    }

    private int resolveAmount(
        BettingRound.Action action,
        BettingRound round,
        User human,
        int playerCurrentBet
    ) {
        if (action == BettingRound.Action.BET) {
            return view.getPlayerBetAmount(round.getBigBlind(), human.getNumbChips());
        }
        if (action == BettingRound.Action.CALL) {
            return round.callAmount(playerCurrentBet);
        }
        if (action == BettingRound.Action.RAISE) {
            return view.getPlayerBetAmount(round.minRaiseAmount(), human.getNumbChips());
        }
        return 0;
    }

    private void onPlayerAction(String connectionPlayerId, LanEnvelope envelope) throws IOException {
        JSONObject payload = envelope.getPayload();
        String claimedId = ActionPayloads.playerIdFromAction(payload);
        if (!connectionPlayerId.equals(claimedId)) {
            notifyActionRejected(connectionPlayerId, "playerId no coincide con la conexión.");
            return;
        }
        if (activePlayerId == null || !activePlayerId.equals(claimedId)) {
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
            view.showUserChips(hostUser.getName(), hostUser.getNumbChips());
            view.showPot(0);
            broadcastState(BettingRound.Phase.RIVER);
            return true;
        }
        return false;
    }

    private void endRound() throws IOException {
        ShowdownResult result = pokerGame.determineShowdownResult();
        int pot = pokerGame.getPot();
        pokerGame.awardPot(result);
        view.showResult(pokerGame.getCommunityCards(), pokerGame.getPlayerHand(), result, pot);
        view.showUserChips(hostUser.getName(), hostUser.getNumbChips());
        broadcastState(BettingRound.Phase.RIVER);
        session.broadcast(new LanEnvelope(
            LanMessageType.HAND_RESULT,
            new JSONObject()
                .put("winner", result.getWinners().isEmpty() ? "" : result.getWinners().get(0).getName())
                .put("pot", pot)
        ));
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
