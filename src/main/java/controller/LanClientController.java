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
    private GameTable lobbyTable;
    private String localDisplayName;

    public LanClientController(GameView view) {
        this.view = view;
    }

    public void run() throws IOException {
        view.awaitUiReady(view.getUiSyncTimeoutMs());

        JoinDecision decision = null;
        while (decision == null || !decision.isAccepted()) {
            LanDialogs.ConnectParams params = LanDialogs.showConnectDialog();
            if (params == null) {
                throw new IllegalStateException("Conexión cancelada");
            }
            client = new GameClient(params.host(), params.port(), this);
            decision = client.connectAndJoin(params.playerName());
            if (!decision.isAccepted()) {
                LanDialogs.showJoinRejection(decision);
                if (!LanDialogs.askRetryJoin()) {
                    throw new IllegalStateException("Unión cancelada");
                }
                client.close();
            }
        }

        localPlayerId = decision.getPlayerId();
        localDisplayName = decision.getDisplayName();
        view.showUserChipsSync(decision.getDisplayName(), 10000, false);
        waitForGameStart();
        runClientGameLoop();
    }

    private void waitForGameStart() {
        // Show GameTable with waiting state
        lobbyTable = GameTable.create();
        lobbyTable.awaitUiReady(5000);
        lobbyTable.show();
        lobbyTable.showLanLobby(localDisplayName, 10000);

        while (!gameRunning) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Espera de inicio interrumpida");
            }
        }

        // Transition from lobby to game — hide lobby indicators
        lobbyTable.hideLanLobby();
    }

    private void runClientGameLoop() throws IOException {
        view.setGameActive(true);
        while (gameRunning) {
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

        BettingRound.Action action = view.waitForPlayerAction(round, me.getCurrentBet(), view.getUiSyncTimeoutMs());
        if (action == null) {
            action = round.canCheck(me.getCurrentBet()) ? BettingRound.Action.CHECK : BettingRound.Action.FOLD;
            view.resolvePendingPlayerAction(action);
        }

        int amount = 0;
        if (action == BettingRound.Action.BET) {
            amount = view.getPlayerBetAmount(round.getBigBlind(), me.getChips());
        } else if (action == BettingRound.Action.CALL) {
            amount = round.callAmount(me.getCurrentBet());
        } else if (action == BettingRound.Action.RAISE) {
            amount = view.getPlayerBetAmount(round.minRaiseAmount(), me.getChips());
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
                    if (lobbyTable != null) {
                        Platform.runLater(() -> lobbyTable.updateLanLobbySeats(seats));
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
        view.showPot(state.getPot());
        if (!state.getCommunityCards().isEmpty()) {
            PlayerStateDto me = findLocalPlayer(state);
            ArrayList<Card> hand = me != null ? new ArrayList<>(me.getHand()) : new ArrayList<>();
            view.showCommunityCards(
                new ArrayList<>(state.getCommunityCards()),
                hand,
                state.getCommunityCards().size()
            );
        }
        PlayerStateDto me = findLocalPlayer(state);
        if (me != null) {
            view.showUserChips(me.getName(), me.getChips());
            if (!me.getHand().isEmpty()) {
                view.showPlayerHand(new ArrayList<>(me.getHand()));
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
