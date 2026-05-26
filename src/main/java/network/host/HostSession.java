package network.host;

import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.contracts.RejectReason;
import network.protocol.JoinPayloads;
import network.protocol.JoinPayloads.LobbyPlayer;
import network.protocol.LanConstants;
import network.protocol.LanEnvelope;
import network.protocol.LanMessageType;
import network.protocol.MessageCodec;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class HostSession {

    private final String sessionId = UUID.randomUUID().toString();
    private final HostJoinHandler joinHandler;
    private final SessionNameRegistry nameRegistry;
    private final NameValidator nameValidator;
    private final Map<String, ConnectedClient> clientsById = new ConcurrentHashMap<>();
    private final List<Runnable> lobbyListeners = new CopyOnWriteArrayList<>();
    private final List<Runnable> disconnectListeners = new CopyOnWriteArrayList<>();
    private volatile boolean gameStarted;
    private String hostPlayerId;
    private String hostDisplayName;
    private PendingActionRegistry pendingActions;
    private Function<String, Integer> chipResolver = name -> 10000;

    public HostSession() {
        SessionNameRegistry registry = new SessionNameRegistry();
        NameValidator validator = new NameValidator();
        this.nameRegistry = registry;
        this.nameValidator = validator;
        this.joinHandler = new HostJoinHandler(
            validator,
            registry,
            (playerId, displayName) -> !gameStarted && seatedHumans() < LanConstants.MAX_HUMAN_PLAYERS,
            () -> UUID.randomUUID().toString()
        );
    }

    public synchronized JoinDecision registerHostPlayer(String proposedName) {
        JoinDecision decision = joinHandler.handleJoin(new JoinRequest(proposedName));
        if (decision.isAccepted()) {
            hostPlayerId = decision.getPlayerId();
            hostDisplayName = decision.getDisplayName();
            notifyLobbyListeners();
        }
        return decision;
    }

    public String getHostPlayerId() {
        return hostPlayerId;
    }

    public String getHostDisplayName() {
        return hostDisplayName;
    }

    private int seatedHumans() {
        int count = clientsById.size();
        if (hostPlayerId != null) {
            count++;
        }
        return count;
    }

    public String getSessionId() {
        return sessionId;
    }

    public synchronized JoinDecision registerClient(java.net.Socket socket, String proposedName) throws IOException {
        if (seatedHumans() >= LanConstants.MAX_HUMAN_PLAYERS) {
            JoinDecision full = JoinDecision.rejected(
                RejectReason.INTERNAL_ERROR,
                "La mesa está llena (máximo " + LanConstants.MAX_HUMAN_PLAYERS + " jugadores)."
            );
            MessageCodec.send(socket, new LanEnvelope(LanMessageType.JOIN_RESPONSE, JoinPayloads.joinResponse(full)));
            socket.close();
            return full;
        }
        JoinDecision decision = joinHandler.handleJoin(new JoinRequest(proposedName));
        if (!decision.isAccepted()) {
            MessageCodec.send(socket, new LanEnvelope(LanMessageType.JOIN_RESPONSE, JoinPayloads.joinResponse(decision)));
            socket.close();
            return decision;
        }

        String normalized = nameValidator.normalize(decision.getDisplayName());
        int chips = chipResolver.apply(decision.getDisplayName());
        ConnectedClient client = new ConnectedClient(
            socket,
            decision.getPlayerId(),
            decision.getDisplayName(),
            normalized
        );
        clientsById.put(decision.getPlayerId(), client);
        JoinDecision withChips = JoinDecision.accepted(decision.getPlayerId(), decision.getDisplayName(), chips);
        try {
            client.send(new LanEnvelope(LanMessageType.JOIN_RESPONSE, JoinPayloads.joinResponse(withChips)));
        } catch (IOException e) {
            // Clean up partial registration if the response fails to send
            clientsById.remove(decision.getPlayerId());
            nameRegistry.unregister(normalized);
            client.close();
            throw e;
        }
        broadcastLobby();
        notifyLobbyListeners();
        return decision;
    }

    public void setPendingActionRegistry(PendingActionRegistry pendingActions) {
        this.pendingActions = pendingActions;
    }

    public void setChipResolver(Function<String, Integer> chipResolver) {
        this.chipResolver = chipResolver;
    }

    public void addDisconnectListener(Runnable listener) {
        disconnectListeners.add(listener);
    }

    public synchronized void removeClient(String playerId) throws IOException {
        ConnectedClient removed = clientsById.remove(playerId);
        if (removed == null) {
            return;
        }
        nameRegistry.unregister(nameValidator.normalize(removed.getDisplayName()));
        removed.close();

        // Cancel any pending action wait for this disconnected player
        if (pendingActions != null) {
            pendingActions.cancel(playerId);
        }

        broadcast(new LanEnvelope(
            LanMessageType.DISCONNECT,
            new org.json.JSONObject()
                .put("playerId", playerId)
                .put("displayName", removed.getDisplayName())
        ));
        if (!gameStarted) {
            broadcastLobby();
        } else {
            // During the game, notify the controller so it can broadcast updated state
            for (Runnable listener : disconnectListeners) {
                listener.run();
            }
        }
        notifyLobbyListeners();
    }

    public List<ConnectedClient> getClients() {
        return new ArrayList<>(clientsById.values());
    }

    public Optional<ConnectedClient> findClient(String playerId) {
        return Optional.ofNullable(clientsById.get(playerId));
    }

    public boolean canStartGame() {
        return !gameStarted && seatedHumans() >= LanConstants.MIN_PLAYERS_TO_START;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public synchronized void markGameStarted() throws IOException {
        gameStarted = true;
        broadcast(new LanEnvelope(
            LanMessageType.START_GAME,
            new org.json.JSONObject().put("message", "La partida ha comenzado")
        ));
    }

    public void broadcast(LanEnvelope envelope) throws IOException {
        for (ConnectedClient client : clientsById.values()) {
            if (client.isOpen()) {
                client.send(envelope);
            }
        }
    }

    public void broadcastLobby() throws IOException {
        broadcast(new LanEnvelope(
            LanMessageType.LOBBY_STATE,
            JoinPayloads.lobbyState(sessionId, lobbyPlayers(), canStartGame())
        ));
    }

    public List<LobbyPlayer> lobbyPlayers() {
        List<LobbyPlayer> players = new ArrayList<>();
        if (hostPlayerId != null) {
            players.add(new LobbyPlayer(hostPlayerId, hostDisplayName));
        }
        for (ConnectedClient client : clientsById.values()) {
            players.add(new LobbyPlayer(client.getPlayerId(), client.getDisplayName()));
        }
        return players;
    }

    public List<UserSeat> buildRemoteSeats() {
        List<UserSeat> seats = new ArrayList<>();
        for (ConnectedClient client : clientsById.values()) {
            seats.add(new UserSeat(client.getPlayerId(), client.getDisplayName()));
        }
        return seats;
    }

    public record UserSeat(String playerId, String displayName) {}

    public void addLobbyListener(Runnable listener) {
        lobbyListeners.add(listener);
    }

    private void notifyLobbyListeners() {
        for (Runnable listener : lobbyListeners) {
            listener.run();
        }
    }
}
