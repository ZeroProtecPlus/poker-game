package model;

import java.util.ArrayList;
import java.util.UUID;

public class User implements Player {
    private final String playerId;
    private final String userName;
    private int numbChips;
    private int currentBet;
    private boolean folded;
    private boolean allIn;
    private ArrayList<Card> hand = new ArrayList<>();
    private PlayerRole role = PlayerRole.NONE;

    public User(String userName) {
        this(UUID.randomUUID().toString(), userName);
    }

    public User(String playerId, String userName) {
        this.playerId = playerId;
        this.userName = userName;
        this.numbChips = 10000;
    }


    public int getNumbChips() {
        return numbChips;
    }

    public void setNumbChips(int numbChips) {
        this.numbChips = numbChips;
    }

    @Override
    public String getName() {
        return userName;
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public int getChips() {
        return numbChips;
    }

    @Override
    public int getCurrentBet() {
        return currentBet;
    }

    @Override
    public boolean isFolded() {
        return folded;
    }

    @Override
    public boolean isAllIn() {
        return allIn;
    }

    @Override
    public PlayerRole getPlayerRole() {
        return role;
    }

    @Override
    public ArrayList<Card> getHand() {
        return new ArrayList<>(hand);
    }

    @Override
    public void setFolded(boolean folded) {
        this.folded = folded;
    }

    @Override
    public void setAllIn(boolean allIn) {
        this.allIn = allIn;
    }

    @Override
    public void setRole(PlayerRole role) {
        this.role = role == null ? PlayerRole.NONE : role;
    }

    @Override
    public void addCard(Card card) {
        hand.add(card);
    }

    @Override
    public void clearHand() {
        hand.clear();
        folded = false;
        allIn = false;
        currentBet = 0;
    }

    @Override
    public int placeBet(int amount) {
        if (amount <= 0 || folded || allIn) return 0;
        int actual = Math.min(amount, numbChips);
        numbChips -= actual;
        currentBet += actual;
        if (numbChips == 0) allIn = true;
        return actual;
    }

    @Override
    public void resetRoundBet() {
        currentBet = 0;
    }
}
