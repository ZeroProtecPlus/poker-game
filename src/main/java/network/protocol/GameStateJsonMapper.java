package network.protocol;

import model.BettingRound;
import model.Card;
import model.PlayerRole;
import model.dto.GameStateDto;
import model.dto.PlayerStateDto;
import org.json.JSONArray;
import org.json.JSONObject;
import util.CardSerializer;

import java.util.ArrayList;
import java.util.List;

public final class GameStateJsonMapper {

    private GameStateJsonMapper() {}

    public static JSONObject toJson(GameStateDto state) {
        JSONObject root = new JSONObject();
        root.put("gameId", state.getGameId());
        root.put("machineId", state.getMachineId());
        root.put("humanChips", state.getHumanChips());
        root.put("pot", state.getPot());
        root.put("communityCards", cardsToJsonArray(state.getCommunityCards()));
        root.put("remainingDeck", cardsToJsonArray(state.getRemainingDeck()));
        root.put("dealerIndex", state.getDealerIndex());
        root.put("currentPhase", state.getCurrentPhase() != null ? state.getCurrentPhase().name() : null);
        root.put("timestamp", state.getTimestamp());

        JSONArray recentActions = new JSONArray();
        for (String action : state.getRecentActions()) {
            recentActions.put(action);
        }
        root.put("recentActions", recentActions);

        JSONArray players = new JSONArray();
        for (PlayerStateDto player : state.getPlayers()) {
            players.put(playerToJson(player));
        }
        root.put("players", players);
        return root;
    }

    public static GameStateDto fromJson(JSONObject root) {
        GameStateDto state = new GameStateDto();
        state.setGameId(root.optString("gameId", null));
        state.setMachineId(root.optString("machineId", null));
        state.setHumanChips(root.optInt("humanChips", 0));
        state.setPot(root.optInt("pot", 0));
        state.setCommunityCards(cardsFromJsonArray(root.optJSONArray("communityCards")));
        state.setRemainingDeck(cardsFromJsonArray(root.optJSONArray("remainingDeck")));
        state.setDealerIndex(root.optInt("dealerIndex", 0));
        String phase = root.optString("currentPhase", null);
        if (phase != null && !phase.isBlank()) {
            state.setCurrentPhase(BettingRound.Phase.valueOf(phase));
        }
        state.setTimestamp(root.optLong("timestamp", System.currentTimeMillis()));

        JSONArray recentActions = root.optJSONArray("recentActions");
        List<String> actionsList = new ArrayList<>();
        if (recentActions != null) {
            for (int i = 0; i < recentActions.length(); i++) {
                actionsList.add(recentActions.optString(i, ""));
            }
        }
        state.setRecentActions(actionsList);

        JSONArray players = root.optJSONArray("players");
        List<PlayerStateDto> list = new ArrayList<>();
        if (players != null) {
            for (int i = 0; i < players.length(); i++) {
                list.add(playerFromJson(players.getJSONObject(i)));
            }
        }
        state.setPlayers(list);
        return state;
    }

    private static JSONObject playerToJson(PlayerStateDto player) {
        JSONObject json = new JSONObject();
        json.put("playerId", player.getPlayerId());
        json.put("name", player.getName());
        json.put("hand", cardsToJsonArray(player.getHand()));
        json.put("chips", player.getChips());
        json.put("currentBet", player.getCurrentBet());
        json.put("folded", player.isFolded());
        json.put("allIn", player.isAllIn());
        json.put("role", player.getRole() != null ? player.getRole().name() : PlayerRole.NONE.name());
        json.put("ai", player.isAi());
        return json;
    }

    private static PlayerStateDto playerFromJson(JSONObject json) {
        PlayerStateDto player = new PlayerStateDto();
        player.setPlayerId(json.optString("playerId", null));
        player.setName(json.optString("name", null));
        player.setHand(cardsFromJsonArray(json.optJSONArray("hand")));
        player.setChips(json.optInt("chips", 0));
        player.setCurrentBet(json.optInt("currentBet", 0));
        player.setFolded(json.optBoolean("folded", false));
        player.setAllIn(json.optBoolean("allIn", false));
        String role = json.optString("role", PlayerRole.NONE.name());
        player.setRole(PlayerRole.valueOf(role));
        player.setAi(json.optBoolean("ai", false));
        return player;
    }

    private static List<Card> cardsFromJsonArray(JSONArray array) {
        if (array == null || array.length() == 0) {
            return new ArrayList<>();
        }
        return CardSerializer.fromJson(array.toString());
    }

    private static JSONArray cardsToJsonArray(List<Card> cards) {
        JSONArray array = new JSONArray();
        for (Card card : cards) {
            array.put(CardSerializer.toCode(card));
        }
        return array;
    }
}
