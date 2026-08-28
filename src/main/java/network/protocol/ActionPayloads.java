package network.protocol;

import model.BettingRound;
import org.json.JSONObject;

public final class ActionPayloads {

    private ActionPayloads() {}

    public static JSONObject playerAction(String playerId, BettingRound.Action action, int amount) {
        JSONObject json = new JSONObject()
            .put("playerId", playerId)
            .put("action", action.name());
        if (amount > 0) {
            json.put("amount", amount);
        }
        return json;
    }

    public static String playerIdFromAction(JSONObject json) {
        return json.getString("playerId");
    }

    public static BettingRound.Action actionFromJson(JSONObject json) {
        return BettingRound.Action.valueOf(json.getString("action"));
    }

    public static int amountFromJson(JSONObject json) {
        return json.optInt("amount", 0);
    }

    public static JSONObject actionRejected(String reason) {
        return new JSONObject().put("reason", reason);
    }

    public static JSONObject gameState(
        long stateSeq,
        String yourPlayerId,
        JSONObject stateJson,
        String activePlayerId
    ) {
        JSONObject json = new JSONObject()
            .put("stateSeq", stateSeq)
            .put("yourPlayerId", yourPlayerId)
            .put("state", stateJson);
        if (activePlayerId != null) {
            json.put("activePlayerId", activePlayerId);
        }
        return json;
    }

    public static long stateSeqFromJson(JSONObject json) {
        return json.getLong("stateSeq");
    }

    public static String yourPlayerIdFromJson(JSONObject json) {
        return json.optString("yourPlayerId", null);
    }

    public static String activePlayerIdFromJson(JSONObject json) {
        return json.optString("activePlayerId", null);
    }
}
