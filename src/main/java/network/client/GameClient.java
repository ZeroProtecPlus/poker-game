package network.client;

import network.contracts.JoinDecision;
import network.protocol.ActionPayloads;
import network.protocol.JoinPayloads;
import network.protocol.LanEnvelope;
import network.protocol.LanMessageType;
import network.protocol.MessageCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameClient implements AutoCloseable {

    public interface Listener {
        void onEnvelope(LanEnvelope envelope);
        void onDisconnected(String reason);
    }

    private final String host;
    private final int port;
    private final Listener listener;
    private final ExecutorService readerExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private String playerId;

    public GameClient(String host, int port, Listener listener) {
        this.host = Objects.requireNonNull(host, "host is required");
        this.port = port;
        this.listener = Objects.requireNonNull(listener, "listener is required");
    }

    public JoinDecision connectAndJoin(String proposedName) throws IOException {
        socket = new Socket(host, port);
        // No handshake timeout — client waits indefinitely for JOIN_RESPONSE
        reader = MessageCodec.openReader(socket);
        writer = MessageCodec.openWriter(socket);

        MessageCodec.send(socket, writer, new LanEnvelope(
            LanMessageType.JOIN_REQUEST,
            JoinPayloads.joinRequest(proposedName)
        ));

        LanEnvelope response = MessageCodec.receive(socket, reader);
        if (response.getType() != LanMessageType.JOIN_RESPONSE) {
            close();
            throw new IOException("expected JOIN_RESPONSE, got " + response.getType());
        }
        JoinDecision decision = JoinPayloads.joinDecisionFromJson(response.getPayload());
        if (decision.isAccepted()) {
            playerId = decision.getPlayerId();
            // Disable read timeout after handshake — readLoop waits indefinitely
            socket.setSoTimeout(0);
            running.set(true);
            readerExecutor.submit(this::readLoop);
        } else {
            close();
        }
        return decision;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void sendAction(model.BettingRound.Action action, int amount) throws IOException {
        if (playerId == null) {
            throw new IllegalStateException("not connected");
        }
        MessageCodec.send(socket, writer, new LanEnvelope(
            LanMessageType.PLAYER_ACTION,
            ActionPayloads.playerAction(playerId, action, amount)
        ));
    }

    private void readLoop() {
        try {
            while (running.get() && socket != null && !socket.isClosed()) {
                LanEnvelope envelope = MessageCodec.receive(socket, reader);
                listener.onEnvelope(envelope);
            }
        } catch (IOException ex) {
            if (running.get()) {
                listener.onDisconnected(ex.getMessage());
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
        readerExecutor.shutdownNow();
    }
}
