package model;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

/**
 * Jugador IA con lógica basada en la fuerza de su mano.
 */
public class AIPlayer implements Player {

    public enum Role {
        DEALER,
        SMALL_BLIND,
        BIG_BLIND,
        NONE,
    }

    private final String name;
    private final String playerId;
    private int chips;
    private int currentBet; // lo que ha apostado en la ronda actual
    private boolean folded;
    private boolean allIn;
    private ArrayList<Card> hand = new ArrayList<>();
    private Role role = Role.NONE;
    private final HandEvaluator handEvaluator = new HandEvaluator();

    private static final Random RNG = new Random();

    public AIPlayer(String name, int startingChips) {
        this(UUID.randomUUID().toString(), name, startingChips);
    }

    public AIPlayer(String playerId, String name, int startingChips) {
        this.playerId = playerId;
        this.name = name;
        this.chips = startingChips;
    }

    // =========================================================================
    //  DECISIÓN DE IA
    // =========================================================================

    /**
     * La IA decide su acción basándose en la fuerza de su mano
     * y las cartas comunitarias disponibles.
     *
     * @param callAmount  cuánto necesita poner para igualar
     * @param pot         tamaño actual del bote
     * @param community   cartas comunitarias visibles
     * @return acción elegida
     */
    public BettingRound.Action decide(
        int callAmount,
        int pot,
        ArrayList<Card> community,
        Role role
    ) {
        if (folded || allIn) return BettingRound.Action.CHECK;

        double strength = evaluateHandStrength(community);
        boolean isBlind = (role == Role.SMALL_BLIND || role == Role.BIG_BLIND);

        if (callAmount == 0) {
            if (strength > 0.65) return BettingRound.Action.RAISE;
            if (isBlind && strength > 0.25) return BettingRound.Action.BET; // proteger ciega
            if (strength > 0.35) return BettingRound.Action.CHECK;
            return RNG.nextDouble() < 0.15
                ? BettingRound.Action.BET
                : BettingRound.Action.CHECK;
        }

        double potOdds = callAmount / (double) (pot + callAmount);
        double raiseThresh = isBlind ? 0.65 : 0.75;
        double foldThresh = isBlind ? 0.15 : 0.20;

        if (
            strength > raiseThresh && chips > callAmount * 2
        ) return BettingRound.Action.RAISE;
        if (strength > potOdds + 0.08) return BettingRound.Action.CALL;
        if (isBlind && strength > foldThresh) return BettingRound.Action.CALL; // defender ciega
        if (
            strength > 0.20 && RNG.nextDouble() < 0.20
        ) return BettingRound.Action.CALL; // bluff ocasional
        return BettingRound.Action.FOLD;
    }

private double evaluateHandStrength(ArrayList<Card> community) {
        if (hand.isEmpty()) return 0.0;

        // Preflop: evaluate using only hole cards
        if (community == null || community.isEmpty()) {
            return evaluatePreflopStrength();
        }

        int totalCards = hand.size() + community.size();
        if (hand.size() < 2 || totalCards < 5) {
            return 0.0;
        }

        HandEvaluator.HandRank rank = handEvaluator.evaluateBestRank(hand, community);
        return rank.ordinal() / (double) (HandEvaluator.HandRank.values().length - 1);
    }

    /**
     * Evalúa la fuerza de la mano en preflop usando solo las 2 hole cards.
     * Score basado en: pares, cartas altas, suited, conectividad.
     */
    private double evaluatePreflopStrength() {
        if (hand.size() < 2) return 0.0;

        Card first = hand.get(0);
        Card second = hand.get(1);

        int v1 = rankValue(first);
        int v2 = rankValue(second);
        int high = Math.max(v1, v2);
        int low = Math.min(v1, v2);
        boolean pair = (v1 == v2);
        boolean suited = first.getSuit().equals(second.getSuit());

        int gap = Math.abs(v1 - v2) - 1;
        if (gap < 0) gap = 0;
        if (gap > 3) gap = 4;

        double score;
        if (pair) {
            // Par: base alta + valor de la carta
            score = 0.56 + ((high - 2) / 12.0) * 0.40;
        } else {
            // No par: carta alta + suited + conectividad
            score = 0.05;
            score += ((high - 2) / 12.0) * 0.35;
            score += ((low - 2) / 12.0) * 0.20;
            if (suited) score += 0.08;
            if (gap == 1) score += 0.06;
            else if (gap == 2) score += 0.03;
            else if (gap >= 4) score -= 0.06;
            if (high >= 11 && low >= 10) score += 0.04;
            if (high == 14 && low >= 10) score += 0.03;
        }

        return Math.max(0.0, Math.min(1.0, score));
    }

    private int rankValue(Card card) {
        String r = card.getRank().toUpperCase();
        return switch (r) {
            case "A" -> 14;
            case "K" -> 13;
            case "Q" -> 12;
            case "J" -> 11;
            default -> {
                int v = Integer.parseInt(r);
                yield (v >= 2 && v <= 10) ? v : 0;
            }
        };
    }

    public int decideAmount(
        int minBet,
        int currentPot,
        ArrayList<Card> community,
        Role role
    ) {
        double strength = evaluateHandStrength(community);
        boolean isBlind = (role == Role.SMALL_BLIND || role == Role.BIG_BLIND);
        double mult = isBlind ? 1.5 : 1.0; // blinds apuestan más para proteger
        int base = (int) (Math.max(minBet, currentPot / 4) * mult);

        if (strength > 0.85) return Math.min(chips, base * 3);
        if (strength > 0.60) return Math.min(chips, base * 2);
        return Math.min(chips, base);
    }

    // =========================================================================
    //  APUESTAS
    // =========================================================================

    /** Pone fichas en el bote. Devuelve lo que realmente pudo poner. */
    @Override
    public int placeBet(int amount) {
        if (amount <= 0 || folded || allIn) return 0;
        int actual = Math.min(amount, chips);
        chips -= actual;
        currentBet += actual;
        if (chips == 0) allIn = true;
        return actual;
    }

    /** Recibe el bote al ganar la mano. */
    public void receivePot(int amount) {
        chips += amount;
    }

    @Override
    public void resetRoundBet() {
        currentBet = 0;
    }

    // =========================================================================
    //  GETTERS / SETTERS
    // =========================================================================

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPlayerId() {
        return playerId;
    }

    @Override
    public int getChips() {
        return chips;
    }

    @Override
    public void setChips(int chips) {
        this.chips = chips;
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
        return toPlayerRole(role);
    }

    public Role getRole() {
        return role;
    }

    @Override
    public ArrayList<Card> getHand() {
        return new ArrayList<>(hand);
    }

    @Override
    public void setFolded(boolean f) {
        this.folded = f;
    }

    @Override
    public void setAllIn(boolean a) {
        this.allIn = a;
    }

    @Override
    public void setRole(PlayerRole role) {
        this.role = toAIRole(role);
    }

    public void setRole(Role r) {
        this.role = r;
    }

    @Override
    public void addCard(Card c) {
        hand.add(c);
    }

    @Override
    public void clearHand() {
        hand.clear();
        folded = false;
        allIn = false;
        currentBet = 0;
    }

    public static PlayerRole toPlayerRole(Role role) {
        if (role == null) return PlayerRole.NONE;
        return switch (role) {
            case DEALER -> PlayerRole.DEALER;
            case SMALL_BLIND -> PlayerRole.SMALL_BLIND;
            case BIG_BLIND -> PlayerRole.BIG_BLIND;
            case NONE -> PlayerRole.NONE;
        };
    }

    public static Role toAIRole(PlayerRole role) {
        if (role == null) return Role.NONE;
        return switch (role) {
            case DEALER -> Role.DEALER;
            case SMALL_BLIND -> Role.SMALL_BLIND;
            case BIG_BLIND -> Role.BIG_BLIND;
            case NONE -> Role.NONE;
        };
    }
}
