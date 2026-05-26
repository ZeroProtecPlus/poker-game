package network.protocol;

public enum LanMessageType {
    JOIN_REQUEST,
    JOIN_RESPONSE,
    LOBBY_STATE,
    START_GAME,
    PLAYER_ACTION,
    ACTION_REJECTED,
    GAME_STATE,
    HAND_RESULT,
    GAME_OVER,
    DISCONNECT
}
