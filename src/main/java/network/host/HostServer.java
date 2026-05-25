package network.host;

import network.protocol.LanConstants;
import network.protocol.LanEnvelope;
import network.protocol.LanMessageType;
import network.protocol.MessageCodec;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class HostServer implements AutoCloseable {

    public interface MessageHandler {
        void onPlayerAction(String playerId, LanEnvelope envelope) throws IOException;
    }

    private final int port;
    private final HostSession session;
    private final MessageHandler messageHandler;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;

    public HostServer(int port, HostSession session, MessageHandler messageHandler) {
        this.port = port;
        this.session = session;
        this.messageHandler = messageHandler;
    }

    public HostSession getSession() {
        return session;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        serverSocket = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        workers.submit(this::acceptLoop);
    }

    public int getPort() {
        return port;
    }

    public static String localAddressHint() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            return "127.0.0.1";
        }
    }

    private void acceptLoop() {
        while (running.get() && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                workers.submit(() -> handleClient(socket));
            } catch (IOException ex) {
                if (running.get()) {
                    System.err.println("HostServer accept error: " + ex.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        String playerId = null;
        try {
            socket.setSoTimeout(30000); // handshake timeout — 30 s to send JOIN_REQUEST
            var reader = MessageCodec.openReader(socket);
            LanEnvelope first = MessageCodec.receive(socket, reader);
            if (first.getType() != LanMessageType.JOIN_REQUEST) {
                return;
            }
            String proposedName = first.getPayload().getString("proposedName");
            var decision = session.registerClient(socket, proposedName);
            if (!decision.isAccepted()) {
                return;
            }
            playerId = decision.getPlayerId();
            ConnectedClient client = session.findClient(playerId).orElseThrow();

            // Disable read timeout after handshake — the readLoop must wait
            // indefinitely for game messages (including lobby idle periods).
            socket.setSoTimeout(0);

            readLoop(client);
        } catch (Exception ex) {
            System.err.println("Host client error: " + ex.getMessage());
            if (playerId != null) {
                try {
                    session.removeClient(playerId);
                } catch (IOException ignored) {
                }
            }
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void readLoop(ConnectedClient client) throws IOException {
        while (client.isOpen()) {
            LanEnvelope envelope = MessageCodec.receive(client.getSocket(), client.getReader());
            if (envelope.getType() == LanMessageType.PLAYER_ACTION) {
                messageHandler.onPlayerAction(client.getPlayerId(), envelope);
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        workers.shutdownNow();
    }
}
