package network.protocol;

import network.contracts.JoinDecision;
import network.contracts.RejectReason;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class JoinPayloads {

    private JoinPayloads() {}

    public static JSONObject joinRequest(String proposedName) {
        return new JSONObject().put("proposedName", proposedName);
    }

    public static JSONObject joinResponse(JoinDecision decision) {
        JSONObject json = new JSONObject();
        json.put("accepted", decision.isAccepted());
        if (decision.isAccepted()) {
            json.put("playerId", decision.getPlayerId());
            json.put("displayName", decision.getDisplayName());
        } else {
            json.put("reasonCode", decision.getReasonCode() != null ? decision.getReasonCode().name() : null);
            json.put("detail", decision.getDetail());
        }
        return json;
    }

    public static JoinDecision joinDecisionFromJson(JSONObject json) {
        if (json.getBoolean("accepted")) {
            return JoinDecision.accepted(json.getString("playerId"), json.getString("displayName"));
        }
        String reason = json.optString("reasonCode", RejectReason.INTERNAL_ERROR.name());
        return JoinDecision.rejected(RejectReason.valueOf(reason), json.optString("detail", ""));
    }

    public static JSONObject lobbyState(String sessionId, List<LobbyPlayer> players, boolean canStart) {
        JSONArray array = new JSONArray();
        for (LobbyPlayer player : players) {
            array.put(new JSONObject()
                .put("playerId", player.playerId())
                .put("displayName", player.displayName()));
        }
        return new JSONObject()
            .put("sessionId", sessionId)
            .put("players", array)
            .put("canStart", canStart)
            .put("minPlayers", LanConstants.MIN_PLAYERS_TO_START)
            .put("maxPlayers", LanConstants.MAX_HUMAN_PLAYERS);
    }

    public static List<LobbyPlayer> lobbyPlayersFromJson(JSONObject json) {
        JSONArray array = json.getJSONArray("players");
        List<LobbyPlayer> players = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.getJSONObject(i);
            players.add(new LobbyPlayer(entry.getString("playerId"), entry.getString("displayName")));
        }
        return players;
    }

    public record LobbyPlayer(String playerId, String displayName) {}
}
