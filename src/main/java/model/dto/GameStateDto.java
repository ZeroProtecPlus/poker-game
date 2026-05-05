package model.dto;

import model.BettingRound;
import model.Card;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de snapshot de juego para persistencia.
 * Contiene lo mínimo necesario para reanudar una mano desde cualquier fase.
 */
public class GameStateDto {

    private String gameId;
    private String machineId;
    private int humanChips;
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

    public String getMachineId() {
        return machineId;
    }

    public void setMachineId(String machineId) {
        this.machineId = machineId;
    }

    public int getHumanChips() {
        return humanChips;
    }

    public void setHumanChips(int humanChips) {
        this.humanChips = humanChips;
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
     * Devuelve el jugador humano (no IA) dentro del snapshot.
     * Se usa como heurística porque el snapshot guarda jugadores planos.
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
