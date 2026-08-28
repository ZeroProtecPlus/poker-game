package network.protocol;

public final class LanConstants {

    public static final int PROTOCOL_VERSION = 1;
    public static final int DEFAULT_PORT = 9876;
    public static final int MAX_HUMAN_PLAYERS = 4;
    public static final int MIN_PLAYERS_TO_START = 2;
    public static final long ACTION_TIMEOUT_MS = 60_000;
    /** Fresh starting chips for every multiplayer session (not loaded from DB). */
    public static final int MULTIPLAYER_STARTING_CHIPS = 10000;

    private LanConstants() {}
}
