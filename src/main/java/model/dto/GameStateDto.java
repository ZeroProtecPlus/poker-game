package model.dto;

import model.BettingRound;
import model.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object representing a snapshot of the game state for persistence.
 * Captures everything needed to resume a hand from any phase.
 */
public class GameStateDto {

    private String gameId;
    private int pot;
    private List<Card> communityCards = new ArrayList<>();
    private List<Card> remainingDeck = new ArrayList<>();
    private int dealerIndex;
    private BettingRound.Phase currentPhase;
    private List<PlayerStateDto> players = new ArrayList<>();
    private long timestamp;

    public GameStateDto() {
        this.timestamp = System.currentTimeMillis();
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public int getPot() {
        return pot;
    }

    public void setPot(int pot) {
        this.pot = pot;
    }

    public List<Card> getCommunityCards() {
        return communityCards;
    }

    public void setCommunityCards(List<Card> communityCards) {
        this.communityCards = communityCards != null ? communityCards : new ArrayList<>();
    }

    public List<Card> getRemainingDeck() {
        return remainingDeck;
    }

    public void setRemainingDeck(List<Card> remainingDeck) {
        this.remainingDeck = remainingDeck != null ? remainingDeck : new ArrayList<>();
    }

    public int getDealerIndex() {
        return dealerIndex;
    }

    public void setDealerIndex(int dealerIndex) {
        this.dealerIndex = dealerIndex;
    }

    public BettingRound.Phase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(BettingRound.Phase currentPhase) {
        this.currentPhase = currentPhase;
    }

    public List<PlayerStateDto> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerStateDto> players) {
        this.players = players != null ? players : new ArrayList<>();
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Finds the human player (non-AI) in the player list.
     * Heuristic: the first player whose id does not start with a known AI prefix,
     * or simply the first player if no heuristic matches.
     */
    public PlayerStateDto findHumanPlayer() {
        for (PlayerStateDto p : players) {
            if (!p.isAi()) {
                return p;
            }
        }
        return players.isEmpty() ? null : players.get(0);
    }
}
