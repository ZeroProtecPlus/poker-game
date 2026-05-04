package model;

import java.util.ArrayList;

public interface Player {
    String getName();

    String getPlayerId();

    int getChips();

    void setChips(int chips);

    int getCurrentBet();

    void setCurrentBet(int bet);

    boolean isFolded();

    boolean isAllIn();

    PlayerRole getPlayerRole();

    ArrayList<Card> getHand();

    void setFolded(boolean folded);

    void setAllIn(boolean allIn);

    void setRole(PlayerRole role);

    void addCard(Card card);

    void clearHand();

    int placeBet(int amount);

    void resetRoundBet();
}
