package network.host;

import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.protocol.LanEnvelope;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Verifies SO_REUSEADDR is set before bind on HostServer.
 *
 * Scenarios:
 * - Host restarts within seconds → no BindException
 * - First-time startup binds cleanly → regression check
 */
public class HostServerReuseAddrTest {

    private static final int TEST_PORT = 15555;

    public static void main(String[] args) throws Exception {
        shouldRestartOnSamePortWithoutBindException();
        shouldStartCleanlyOnFirstBind();
        shouldFailWhenPortAlreadyActivelyUsed();
        System.out.println("HostServerReuseAddrTest: all tests passed");
    }

    /**
     * Scenario: Host restarts server within seconds of a previous session ending.
     * Verifies that setReuseAddress(true) allows rebinding on TIME_WAIT port.
     */
    private static void shouldRestartOnSamePortWithoutBindException() throws Exception {
        // Start and stop a host server to put the port into TIME_WAIT
        HostSession session1 = new HostSession();
        session1.registerHostPlayer("TestHost");
        HostServer server1 = new HostServer(TEST_PORT, session1, (id, env) -> {});
        server1.start();
        Thread.sleep(100); // let the socket bind
        server1.close();
        Thread.sleep(50);  // brief wait for socket cleanup

        // Try binding again — SO_REUSEADDR should allow this
        HostSession session2 = new HostSession();
        session2.registerHostPlayer("TestHost2");
        HostServer server2 = new HostServer(TEST_PORT, session2, (id, env) -> {});

        boolean threwBindException = false;
        try {
            server2.start();
            Thread.sleep(50);
        } catch (BindException e) {
            threwBindException = true;
        } finally {
            server2.close();
        }

        require(!threwBindException,
            "SO_REUSEADDR should allow rebinding on same port after close; "
                + "BindException was thrown");
    }

    /**
     * Scenario: First-time host startup on a port.
     * Regression check: SO_REUSEADDR must not break clean startup.
     */
    private static void shouldStartCleanlyOnFirstBind() throws Exception {
        HostSession session = new HostSession();
        session.registerHostPlayer("FirstHost");
        HostServer server = new HostServer(TEST_PORT + 1, session, (id, env) -> {});

        try {
            server.start();
            Thread.sleep(50);
            // Server started successfully — no exception
        } finally {
            server.close();
        }
        // If we reach here, clean bind succeeded
    }

    /**
     * Scenario: Two hosts on same port simultaneously.
     * SO_REUSEADDR does NOT allow two active listeners on the same port.
     */
    private static void shouldFailWhenPortAlreadyActivelyUsed() throws Exception {
        HostSession session1 = new HostSession();
        session1.registerHostPlayer("ActiveHost");
        HostServer server1 = new HostServer(TEST_PORT + 2, session1, (id, env) -> {});
        server1.start();
        Thread.sleep(100);

        HostSession session2 = new HostSession();
        session2.registerHostPlayer("ConflictHost");
        HostServer server2 = new HostServer(TEST_PORT + 2, session2, (id, env) -> {});

        boolean threwBindException = false;
        try {
            server2.start();
        } catch (BindException e) {
            threwBindException = true;
        } catch (IOException e) {
            // Some JDKs wrap BindException in IOException
            if (e.getMessage() != null && e.getMessage().contains("already in use")) {
                threwBindException = true;
            }
        } finally {
            server1.close();
            try { server2.close(); } catch (Exception ignored) {}
        }

        require(threwBindException,
            "Active listener conflict should still throw BindException; "
                + "SO_REUSEADDR only helps TIME_WAIT, not active sockets");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
