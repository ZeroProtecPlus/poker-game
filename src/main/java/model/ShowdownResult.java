package model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ShowdownResult {
    private final List<Player> winners;
    private final PokerGame.HandRank bestRank;
    private final boolean tie;

    public ShowdownResult(List<Player> winners, PokerGame.HandRank bestRank, boolean tie) {
        this.winners = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(winners, "winners cannot be null")));
        this.bestRank = Objects.requireNonNull(bestRank, "bestRank cannot be null");
        this.tie = tie;
    }

    public List<Player> getWinners() {
        return winners;
    }

    public PokerGame.HandRank getBestRank() {
        return bestRank;
    }

    public boolean isTie() {
        return tie;
    }
}
