package network.host;

import network.contracts.JoinDecision;
import network.protocol.JoinPayloads;
import network.protocol.LanConstants;
import network.protocol.LanEnvelope;
import network.protocol.MessageCodec;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class HostSessionAdmissionTest {

    public static void main(String[] args) throws Exception {
        shouldRejectFifthPlayer();
        shouldAcceptUpToFourHumans();
        System.out.println("HostSessionAdmissionTest: all tests passed");
    }

    private static void shouldRejectFifthPlayer() throws Exception {
        HostSession session = new HostSession();
        session.registerHostPlayer("Host");
        String[] remoteNames = {"Alice", "Bob", "Carl"};
        for (int i = 0; i < LanConstants.MAX_HUMAN_PLAYERS - 1; i++) {
            require(joinRemote(session, remoteNames[i]).isAccepted(), "setup player " + i);
        }
        JoinDecision fifth = joinRemote(session, "Diana");
        require(!fifth.isAccepted(), "fifth human should be rejected");
        require(fifth.getDetail().contains("llena"), "should mention full table");
    }

    private static void shouldAcceptUpToFourHumans() throws Exception {
        HostSession session = new HostSession();
        session.registerHostPlayer("Host");
        for (int i = 0; i < 3; i++) {
            String[] names = {"Elena", "Fabio", "Gina"};
            JoinDecision decision = joinRemote(session, names[i]);
            require(decision.isAccepted(), "client " + i + " should join");
        }
        require(session.lobbyPlayers().size() == 4, "lobby should list 4 players");
        require(session.canStartGame(), "should allow start with 4 humans");
    }

    private static JoinDecision joinRemote(HostSession session, String name) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();
            JoinDecision[] clientView = new JoinDecision[1];

            Thread connector = new Thread(() -> {
                try (Socket client = new Socket("127.0.0.1", port)) {
                    LanEnvelope envelope = MessageCodec.receive(client, MessageCodec.openReader(client));
                    if (envelope.getType() == network.protocol.LanMessageType.JOIN_RESPONSE) {
                        clientView[0] = JoinPayloads.joinDecisionFromJson(envelope.getPayload());
                    }
                } catch (IOException ignored) {
                }
            });
            connector.start();
            Socket hostSide = serverSocket.accept();
            JoinDecision hostDecision = session.registerClient(hostSide, name);
            connector.join(3000);
            hostSide.close();
            return hostDecision;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", ex);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
