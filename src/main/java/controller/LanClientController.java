package controller;

import javafx.application.Platform;
import javafx.scene.control.Alert;

import model.BettingRound;
import model.Card;
import model.PokerGame;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import network.client.GameClient;
import network.contracts.JoinDecision;
import network.protocol.ActionPayloads;
import network.protocol.GameStateJsonMapper;
import network.protocol.JoinPayloads;
import network.protocol.LanEnvelope;
import network.protocol.LanMessageType;
import view.GameView;
import view.LanDialogs;
import view.fx.GameTable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class LanClientController implements GameClient.Listener {

    private final GameView view;
    private GameClient client;
    private String localPlayerId;
    private final AtomicLong lastStateSeq = new AtomicLong(-1);
    private volatile GameStateDto latestState;
    private volatile String activePlayerId;
    private volatile boolean gameRunning;
    private volatile long lastActedStateSeq = -1;
    private String lastLobbyRoster = "";
    private GameTable table;
    private String localDisplayName;
    private volatile boolean exitRequested;

    public LanClientController(GameView view) {
        this.view = view;
    }

    public void run() throws IOException {
        // Create GameTable early so the player sees it during connection
        table = GameTable.create();
        table.awaitUiReady(5000);
        table.show();

        JoinDecision decision = null;
        while (decision == null || !decision.isAccepted()) {
            LanDialogs.ConnectParams params = LanDialogs.showConnectDialog();
            if (params == null) {
                table.requestGracefulShutdown();
                throw new IllegalStateException("Conexión cancelada");
            }
            client = new GameClient(params.host(), params.port(), this);
            decision = client.connectAndJoin(params.playerName());
            if (!decision.isAccepted()) {
                LanDialogs.showJoinRejection(decision);
                if (!LanDialogs.askRetryJoin()) {
                    table.requestGracefulShutdown();
                    throw new IllegalStateException("Unión cancelada");
                }
                client.close();
            }
        }

        localPlayerId = decision.getPlayerId();
        localDisplayName = decision.getDisplayName();
        table.showUserChipsSync(decision.getDisplayName(), 10000, false);
        waitForGameStart();
        runClientGameLoop();
    }

    private void waitForGameStart() {
        // Create GameTable for lobby AND game rendering
        table = GameTable.create();
        table.awaitUiReady(5000);
        table.show();
        table.showLanLobby(localDisplayName, 10000);
        table.setOnExitConfirmed(() -> exitRequested = true);

        while (!gameRunning && !exitRequested) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Espera de inicio interrumpida");
            }
        }

        if (exitRequested) {
            table.hideFrame();
            if (client != null) client.close();
            throw new IllegalStateException("Juego cancelado");
        }

        // Transition from lobby to game — clear lobby placeholders
        // but keep the GameTable visible for game rendering.
        table.clearTable();
    }

    private void runClientGameLoop() throws IOException {
        while (gameRunning && !exitRequested) {
            long seq = lastStateSeq.get();
            GameStateDto state = latestState;
            if (state != null
                && localPlayerId.equals(activePlayerId)
                && seq > lastActedStateSeq) {
                lastActedStateSeq = seq;
                handleLocalTurn(state);
            }
            try {
                Thread.sleep(80);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handleLocalTurn(GameStateDto state) throws IOException {
        PlayerStateDto me = findLocalPlayer(state);
        if (me == null || me.isFolded() || me.isAllIn()) {
            return;
        }

        BettingRound.Phase phase = state.getCurrentPhase() != null
            ? state.getCurrentPhase()
            : BettingRound.Phase.PREFLOP;
        int highBet = maxBet(state);
        BettingRound round = new BettingRound(phase, state.getPot(), highBet, PokerGame.BIG_BLIND);

        BettingRound.Action action = table.awaitPlayerAction(round, me.getCurrentBet(), table.getUiSyncTimeoutMs());
        if (action == null) {
            action = round.canCheck(me.getCurrentBet()) ? BettingRound.Action.CHECK : BettingRound.Action.FOLD;
            table.resolvePendingPlayerAction(action);
        }

        int amount = 0;
        if (action == BettingRound.Action.BET) {
            amount = table.getPlayerBetAmount(round.getBigBlind(), me.getChips());
        } else if (action == BettingRound.Action.CALL) {
            amount = round.callAmount(me.getCurrentBet());
        } else if (action == BettingRound.Action.RAISE) {
            amount = table.getPlayerBetAmount(round.minRaiseAmount(), me.getChips());
        }

        client.sendAction(action, amount);
    }

    private int maxBet(GameStateDto state) {
        int high = 0;
        for (PlayerStateDto player : state.getPlayers()) {
            if (!player.isFolded()) {
                high = Math.max(high, player.getCurrentBet());
            }
        }
        return high;
    }

    private PlayerStateDto findLocalPlayer(GameStateDto state) {
        for (PlayerStateDto player : state.getPlayers()) {
            if (localPlayerId.equals(player.getPlayerId())) {
                return player;
            }
        }
        return null;
    }

    @Override
    public void onEnvelope(LanEnvelope envelope) {
        try {
            switch (envelope.getType()) {
                case LOBBY_STATE -> {
                    var players = JoinPayloads.lobbyPlayersFromJson(envelope.getPayload());
                    // Update GameTable lobby seats with remote players
                    List<GameTable.LanSeatInfo> seats = new ArrayList<>();
                    for (var player : players) {
                        // Skip ourselves — we're already shown in the player badge
                        if (!localPlayerId.equals(player.playerId())) {
                            seats.add(new GameTable.LanSeatInfo(player.displayName(), 10000));
                        }
                    }
                    if (table != null) {
                        Platform.runLater(() -> table.updateLanLobbySeats(seats));
                    }
                }
                case START_GAME -> gameRunning = true;
                case GAME_STATE -> applyGameState(envelope.getPayload());
                case ACTION_REJECTED -> Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("LAN");
                    alert.setHeaderText(null);
                    alert.setContentText(envelope.getPayload().optString("reason", "Acción rechazada"));
                    alert.showAndWait();
                });
                case DISCONNECT -> { /* host notifies leave */ }
                case HAND_RESULT -> { /* optional toast */ }
                default -> { }
            }
        } catch (Exception ex) {
            System.err.println("Client envelope error: " + ex.getMessage());
        }
    }

    private void applyGameState(org.json.JSONObject payload) {
        long seq = ActionPayloads.stateSeqFromJson(payload);
        if (seq <= lastStateSeq.get()) {
            return;
        }
        lastStateSeq.set(seq);
        activePlayerId = ActionPayloads.activePlayerIdFromJson(payload);
        GameStateDto state = GameStateJsonMapper.fromJson(payload.getJSONObject("state"));
        latestState = state;

        Platform.runLater(() -> renderState(state));
    }

    private void renderState(GameStateDto state) {
        table.updatePot(state.getPot());
        if (!state.getCommunityCards().isEmpty()) {
            PlayerStateDto me = findLocalPlayer(state);
            ArrayList<Card> hand = me != null ? new ArrayList<>(me.getHand()) : new ArrayList<>();
            table.showCommunityCards(
                new ArrayList<>(state.getCommunityCards()),
                hand,
                state.getCommunityCards().size()
            );
        }
        PlayerStateDto me = findLocalPlayer(state);
        if (me != null) {
            table.showUserChips(me.getName(), me.getChips());
            if (!me.getHand().isEmpty()) {
                table.showPlayerHand(new ArrayList<>(me.getHand()));
            }
        }
    }

    @Override
    public void onDisconnected(String reason) {
        gameRunning = false;
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("LAN");
            alert.setHeaderText(null);
            alert.setContentText("Desconectado del host: " + reason);
            alert.showAndWait();
        });
    }
}
