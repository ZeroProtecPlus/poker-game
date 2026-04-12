package model;

import java.util.*;

public class PokerGame {

    public static final int SMALL_BLIND = 5;
    public static final int BIG_BLIND = 10;

    public enum HandRank {
        HIGH_CARD("Carta Alta"),
        ONE_PAIR("Par"),
        TWO_PAIR("Doble Par"),
        THREE_OF_A_KIND("Trío"),
        STRAIGHT("Escalera"),
        FLUSH("Color"),
        FULL_HOUSE("Full House"),
        FOUR_OF_A_KIND("Póker"),
        STRAIGHT_FLUSH("Escalera de Color"),
        ROYAL_FLUSH("Escalera Real");

        public final String spanishName;

        HandRank(String name) {
            this.spanishName = name;
        }
    }

    private static final Map<String, Integer> RANK_VALUE = new LinkedHashMap<>();

    static {
        RANK_VALUE.put("2", 2);
        RANK_VALUE.put("3", 3);
        RANK_VALUE.put("4", 4);
        RANK_VALUE.put("5", 5);
        RANK_VALUE.put("6", 6);
        RANK_VALUE.put("7", 7);
        RANK_VALUE.put("8", 8);
        RANK_VALUE.put("9", 9);
        RANK_VALUE.put("10", 10);
        RANK_VALUE.put("J", 11);
        RANK_VALUE.put("Q", 12);
        RANK_VALUE.put("K", 13);
        RANK_VALUE.put("A", 14);
    }

    private Deck deck;
    private final HandEvaluator handEvaluator;
    private final User player;
    private final ArrayList<AIPlayer> aiPlayers = new ArrayList<>();
    private final ArrayList<Player> players = new ArrayList<>();
    private final ArrayList<Card> communityCards = new ArrayList<>();

    private int dealerIndex = 0;
    private int pot = 0;

    public PokerGame(User player) {
        this.player = player;
        this.handEvaluator = new HandEvaluator();
        if (player != null) {
            aiPlayers.add(new AIPlayer("Carlos", 10000));
            aiPlayers.add(new AIPlayer("María", 10000));
            aiPlayers.add(new AIPlayer("Sofía", 10000));
        }
        rebuildPlayers();
    }

    private void rebuildPlayers() {
        players.clear();
        if (player != null) {
            players.add(player);
        }
        players.addAll(aiPlayers);
    }

    public void startNewRound() {
        deck = new Deck();
        communityCards.clear();
        pot = 0;

        for (Player current : players) {
            current.clearHand();
        }

        assignRoles();
        dealAllHands();
        postBlinds();
    }

    private void assignRoles() {
        if (players.isEmpty()) {
            return;
        }

        for (int i = 0; i < players.size(); i++) {
            PlayerRole role = switch (i) {
                case 0 -> PlayerRole.DEALER;
                case 1 -> PlayerRole.SMALL_BLIND;
                case 2 -> PlayerRole.BIG_BLIND;
                default -> PlayerRole.NONE;
            };

            int seat = (dealerIndex + i) % players.size();
            players.get(seat).setRole(role);
        }

        dealerIndex = (dealerIndex + 1) % players.size();
    }

    public AIPlayer.Role getHumanRole() {
        return AIPlayer.toAIRole(player.getPlayerRole());
    }

    private void dealAllHands() {
        for (int i = 0; i < 2; i++) {
            for (Player current : players) {
                current.addCard(deck.dealCard());
            }
        }
    }

    private void postBlinds() {
        for (Player current : players) {
            if (current.getPlayerRole() == PlayerRole.SMALL_BLIND) {
                pot += current.placeBet(SMALL_BLIND);
            } else if (current.getPlayerRole() == PlayerRole.BIG_BLIND) {
                pot += current.placeBet(BIG_BLIND);
            }
        }
    }

    public void dealFlop() {
        burnCard();
        for (int i = 0; i < 3; i++) {
            communityCards.add(deck.dealCard());
        }
    }

    public void dealTurnOrRiver() {
        burnCard();
        communityCards.add(deck.dealCard());
    }

    private void burnCard() {
        deck.dealCard();
    }

    public BettingRound createBettingRound(BettingRound.Phase phase) {
        int highBet = 0;
        for (Player current : players) {
            if (!current.isFolded()) {
                highBet = Math.max(highBet, current.getCurrentBet());
            }
        }
        return new BettingRound(phase, pot, highBet, BIG_BLIND);
    }

    public boolean isPlayerAllIn() {
        return player.isAllIn();
    }

    public void humanCheck() {
    }

    public void humanBet(int amount) {
        int actual = player.placeBet(amount);
        pot += actual;
    }

    public int humanCall(int callAmount) {
        int actual = player.placeBet(callAmount);
        pot += actual;
        return actual;
    }

    public void humanRaise(int totalAmount) {
        int extra = Math.max(0, totalAmount - player.getCurrentBet());
        int actual = player.placeBet(extra);
        pot += actual;
    }

    public void humanAllIn() {
        if (!player.isAllIn()) {
            humanBet(player.getChips());
        }
    }

    public AIBettingResult runAIBettingRound(int currentHighBet) {
        List<String> log = new ArrayList<>();

        for (AIPlayer ai : aiPlayers) {
            if (ai.isFolded() || ai.isAllIn()) {
                continue;
            }

            int callAmount = Math.max(0, currentHighBet - ai.getCurrentBet());
            BettingRound.Action action = ai.decide(callAmount, pot, communityCards, ai.getRole());

            switch (action) {
                case FOLD -> {
                    ai.setFolded(true);
                    log.add(ai.getName() + " se retira");
                }
                case CHECK -> log.add(ai.getName() + " pasa");
                case CALL -> {
                    int amount = ai.placeBet(callAmount);
                    pot += amount;
                    log.add(ai.getName() + " iguala " + amount);
                }
                case BET, RAISE -> {
                    int amount = ai.decideAmount(BIG_BLIND, pot, communityCards, ai.getRole());
                    amount = ai.placeBet(amount);
                    pot += amount;
                    currentHighBet = Math.max(currentHighBet, ai.getCurrentBet());
                    String word = action == BettingRound.Action.BET ? "apuesta" : "sube a";
                    log.add(ai.getName() + " " + word + " " + amount);
                }
                default -> log.add(ai.getName() + " pasa");
            }
        }
        return new AIBettingResult(log, currentHighBet);
    }

    public AIBettingResult runUnifiedBettingRound(int currentHighBet, BettingRound.Action humanAction, int humanAmount) {
        List<String> log = new ArrayList<>();

        for (Player current : players) {
            if (current.isFolded() || current.isAllIn()) {
                continue;
            }

            if (current == player) {
                currentHighBet = applyHumanActionInUnifiedRound(humanAction, humanAmount, currentHighBet);
                continue;
            }

            AIPlayer ai = (AIPlayer) current;
            int callAmount = Math.max(0, currentHighBet - ai.getCurrentBet());
            BettingRound.Action action = ai.decide(callAmount, pot, communityCards, ai.getRole());

            switch (action) {
                case FOLD -> {
                    ai.setFolded(true);
                    log.add(ai.getName() + " se retira");
                }
                case CHECK -> log.add(ai.getName() + " pasa");
                case CALL -> {
                    int amount = ai.placeBet(callAmount);
                    pot += amount;
                    log.add(ai.getName() + " iguala " + amount);
                }
                case BET, RAISE -> {
                    int amount = ai.decideAmount(BIG_BLIND, pot, communityCards, ai.getRole());
                    amount = ai.placeBet(amount);
                    pot += amount;
                    currentHighBet = Math.max(currentHighBet, ai.getCurrentBet());
                    String word = action == BettingRound.Action.BET ? "apuesta" : "sube a";
                    log.add(ai.getName() + " " + word + " " + amount);
                }
                default -> log.add(ai.getName() + " pasa");
            }
        }

        return new AIBettingResult(log, currentHighBet, player.isFolded());
    }

    private int applyHumanActionInUnifiedRound(BettingRound.Action action, int humanAmount, int currentHighBet) {
        if (action == null) {
            return currentHighBet;
        }

        switch (action) {
            case FOLD -> player.setFolded(true);
            case CHECK -> {
            }
            case CALL -> {
                int actual = player.placeBet(humanAmount);
                pot += actual;
            }
            case BET -> {
                int actual = player.placeBet(humanAmount);
                pot += actual;
                currentHighBet = Math.max(currentHighBet, player.getCurrentBet());
            }
            case RAISE -> {
                int extra = Math.max(0, humanAmount - player.getCurrentBet());
                int actual = player.placeBet(extra);
                pot += actual;
                currentHighBet = Math.max(currentHighBet, player.getCurrentBet());
            }
            case ALL_IN -> {
                if (!player.isAllIn()) {
                    int actual = player.placeBet(player.getChips());
                    pot += actual;
                    currentHighBet = Math.max(currentHighBet, player.getCurrentBet());
                }
            }
        }

        return currentHighBet;
    }

    public static class AIBettingResult {
        public final List<String> log;
        public final int highBet;
        public final boolean humanFolded;

        public AIBettingResult(List<String> log, int highBet) {
            this(log, highBet, false);
        }

        public AIBettingResult(List<String> log, int highBet, boolean humanFolded) {
            this.log = log;
            this.highBet = highBet;
            this.humanFolded = humanFolded;
        }
    }

    public void resetRoundBets() {
        for (Player current : players) {
            current.resetRoundBet();
        }
    }

    public HandRank evaluateBestHand() {
        HandEvaluator.HandRank evaluatorRank = handEvaluator.evaluateBestRank(player.getHand(), communityCards);
        return toPokerGameRank(evaluatorRank);
    }

    public HandRank evaluateCards(List<Card> cards) {
        if (cards.size() < 5) {
            return HandRank.HIGH_CARD;
        }

        ArrayList<Card> holeCards = new ArrayList<>(cards.subList(0, 2));
        ArrayList<Card> community = new ArrayList<>(cards.subList(2, cards.size()));
        HandEvaluator.HandRank evaluatorRank = handEvaluator.evaluateBestRank(holeCards, community);
        return toPokerGameRank(evaluatorRank);
    }

    private HandRank toPokerGameRank(HandEvaluator.HandRank evaluatorRank) {
        return switch (evaluatorRank) {
            case HIGH_CARD -> HandRank.HIGH_CARD;
            case ONE_PAIR -> HandRank.ONE_PAIR;
            case TWO_PAIR -> HandRank.TWO_PAIR;
            case THREE_OF_A_KIND -> HandRank.THREE_OF_A_KIND;
            case STRAIGHT -> HandRank.STRAIGHT;
            case FLUSH -> HandRank.FLUSH;
            case FULL_HOUSE -> HandRank.FULL_HOUSE;
            case FOUR_OF_A_KIND -> HandRank.FOUR_OF_A_KIND;
            case STRAIGHT_FLUSH -> HandRank.STRAIGHT_FLUSH;
            case ROYAL_FLUSH -> HandRank.ROYAL_FLUSH;
        };
    }

    public void awardPotToPlayer() {
        player.setNumbChips(player.getNumbChips() + pot);
        pot = 0;
    }

    public void awardPotToAI(AIPlayer winner) {
        winner.receivePot(pot);
        pot = 0;
    }

    public void awardPotTo(Player winner) {
        if (winner instanceof User userWinner) {
            userWinner.setNumbChips(userWinner.getNumbChips() + pot);
        } else if (winner instanceof AIPlayer aiWinner) {
            aiWinner.receivePot(pot);
        }
        pot = 0;
    }

    public Player determineWinnerPlayer() {
        Player bestPlayer = null;
        HandRank bestRank = HandRank.HIGH_CARD;

        for (Player current : players) {
            if (current.isFolded()) {
                continue;
            }

            ArrayList<Card> cards = new ArrayList<>(current.getHand());
            cards.addAll(communityCards);
            HandRank rank = evaluateCards(cards);

            if (bestPlayer == null || rank.ordinal() > bestRank.ordinal()) {
                bestPlayer = current;
                bestRank = rank;
                continue;
            }

            if (rank.ordinal() == bestRank.ordinal() && current == player && bestPlayer != player) {
                bestPlayer = current;
            }
        }

        return bestPlayer;
    }

    public ArrayList<Card> getPlayerHand() {
        return new ArrayList<>(player.getHand());
    }

    public ArrayList<Card> getCommunityCards() {
        return new ArrayList<>(communityCards);
    }

    public List<AIPlayer> getAIPlayers() {
        return Collections.unmodifiableList(aiPlayers);
    }

    public List<Player> getPlayers() {
        return Collections.unmodifiableList(players);
    }

    public int getPot() {
        return pot;
    }

    public int getPlayerCurrentBet() {
        return player.getCurrentBet();
    }
}
