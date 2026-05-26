package network.host;

import network.protocol.LanEnvelope;
import network.protocol.MessageCodec;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Socket;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConnectedClient {

    private final Socket socket;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final String playerId;
    private final String displayName;
    private final String normalizedName;
    private final AtomicBoolean open = new AtomicBoolean(true);

    public ConnectedClient(
        Socket socket,
        String playerId,
        String displayName,
        String normalizedName
    ) throws IOException {
        this.socket = Objects.requireNonNull(socket, "socket is required");
        this.reader = MessageCodec.openReader(socket);
        this.writer = MessageCodec.openWriter(socket);
        this.playerId = Objects.requireNonNull(playerId, "playerId is required");
        this.displayName = Objects.requireNonNull(displayName, "displayName is required");
        this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName is required");
    }

    public Socket getSocket() {
        return socket;
    }

    public BufferedReader getReader() {
        return reader;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public boolean isOpen() {
        return open.get() && !socket.isClosed();
    }

    public void send(LanEnvelope envelope) throws IOException {
        if (!isOpen()) {
            throw new IOException("client connection closed");
        }
        MessageCodec.send(socket, writer, envelope);
    }

    public void close() {
        if (!open.compareAndSet(true, false)) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
