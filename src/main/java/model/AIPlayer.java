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

    // ── Decision Constants (FR-05) ──────────────────────────────────────────
    public static final double PREFLOP_CALL_MARGIN = 0.22;
    public static final double POSTFLOP_CALL_MARGIN = 0.08;
    public static final double SPR_COMMIT_THRESHOLD = 0.70;
    public static final double SPR_FOLD_RATIO = 0.35;
    public static final double PREFLOP_BLIND_BET_THRESHOLD = 0.50;
    public static final double PREFLOP_BLIND_CALL_THRESHOLD = 0.30;
    public static final double PREFLOP_BLIND_RAISE_THRESHOLD = 0.75;
    public static final int MAX_CALLS_PER_PHASE = 2;
    public static final int MAX_RAISES_PER_PHASE = 1;
    public static final int PREFLOP_RAISE_MULTIPLIER = 3;
    public static final int PREFLOP_RAISE_CAP_MULTIPLIER = 6;

    // ── Action Counters (FR-03) ────────────────────────────────────────────
    private int roundCallCount = 0;
    private int roundRaiseCount = 0;

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
     * La IA decide su acción basándose en la fuerza de su mano,
     * las cartas comunitarias disponibles y la fase de apuesta.
     *
     * @param callAmount  cuánto necesita poner para igualar
     * @param pot         tamaño actual del bote
     * @param community   cartas comunitarias visibles
     * @param role        rol del jugador (DEALER, SMALL_BLIND, BIG_BLIND, NONE)
     * @param phase       fase de apuesta (PREFLOP, FLOP, TURN, RIVER)
     * @return acción elegida
     */
    public BettingRound.Action decide(
        int callAmount,
        int pot,
        ArrayList<Card> community,
        Role role,
        BettingRound.Phase phase
    ) {
        if (folded || allIn) return BettingRound.Action.CHECK;

        double strength = evaluateHandStrength(community);
        boolean isBlind = (role == Role.SMALL_BLIND || role == Role.BIG_BLIND);
        boolean isPreflop = (phase == BettingRound.Phase.PREFLOP);

        // FR-01: SPR Protection Gate — fold expensive calls with marginal hands
        if (callAmount > chips * SPR_FOLD_RATIO && strength < SPR_COMMIT_THRESHOLD) {
            return callAmount == 0 ? BettingRound.Action.CHECK : BettingRound.Action.FOLD;
        }

        // Phase-aware margins and thresholds
        double callMargin = isPreflop ? PREFLOP_CALL_MARGIN : POSTFLOP_CALL_MARGIN;
        double blindBetThresh = isPreflop ? PREFLOP_BLIND_BET_THRESHOLD : 0.25;
        double blindCallThresh = isPreflop ? PREFLOP_BLIND_CALL_THRESHOLD : 0.15;
        double blindRaiseThresh = isPreflop ? PREFLOP_BLIND_RAISE_THRESHOLD : 0.65;

        if (callAmount == 0) {
            // No call to match — check, bet, or raise
            if (strength > 0.65) {
                // FR-03: Action cap — only one raise/bet per phase
                if (roundRaiseCount >= MAX_RAISES_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                    return BettingRound.Action.CHECK;
                }
                roundRaiseCount++;
                return BettingRound.Action.RAISE;
            }
            if (isBlind && strength > blindBetThresh) {
                if (roundRaiseCount >= MAX_RAISES_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                    return BettingRound.Action.CHECK;
                }
                roundRaiseCount++;
                return BettingRound.Action.BET;
            }
            if (strength > 0.35) return BettingRound.Action.CHECK;
            if (roundRaiseCount >= MAX_RAISES_PER_PHASE) {
                return BettingRound.Action.CHECK;
            }
            return RNG.nextDouble() < 0.15
                ? BettingRound.Action.BET
                : BettingRound.Action.CHECK;
        }

        // Must call, raise, or fold
        double potOdds = callAmount / (double) (pot + callAmount);
        double raiseThresh = isPreflop ? blindRaiseThresh : (isBlind ? 0.65 : 0.75);

        // FR-03: Action cap on raises
        if (strength > raiseThresh && chips > callAmount * 2) {
            if (roundRaiseCount >= MAX_RAISES_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                // Can't raise again — fall through to call/fold
            } else {
                roundRaiseCount++;
                return BettingRound.Action.RAISE;
            }
        }

        // Call decision with phase-aware margin
        if (strength > potOdds + callMargin) {
            // FR-03: Action cap on calls
            if (roundCallCount >= MAX_CALLS_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                return BettingRound.Action.FOLD;
            }
            roundCallCount++;
            return BettingRound.Action.CALL;
        }

        // Blind defense with phase-aware threshold
        double blindFoldThresh = isPreflop ? blindCallThresh : 0.15;
        if (isBlind && strength > blindFoldThresh) {
            if (roundCallCount >= MAX_CALLS_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                return BettingRound.Action.FOLD;
            }
            roundCallCount++;
            return BettingRound.Action.CALL;
        }

        // Occasional bluff
        if (strength > 0.20 && RNG.nextDouble() < 0.20) {
            if (roundCallCount >= MAX_CALLS_PER_PHASE && strength < SPR_COMMIT_THRESHOLD) {
                return BettingRound.Action.FOLD;
            }
            roundCallCount++;
            return BettingRound.Action.CALL;
        }

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
        Role role,
        BettingRound.Phase phase
    ) {
        double strength = evaluateHandStrength(community);
        boolean isBlind = (role == Role.SMALL_BLIND || role == Role.BIG_BLIND);
        double mult = isBlind ? 1.5 : 1.0;

        int base;
        int cap;

        if (phase == BettingRound.Phase.PREFLOP) {
            // FR-04: Fixed preflop raise sizing — decouples from pot
            base = (int) Math.max(minBet, minBet * PREFLOP_RAISE_MULTIPLIER * mult);
            cap = minBet * PREFLOP_RAISE_CAP_MULTIPLIER;
        } else {
            // Postflop: keep current pot-relative formula
            base = (int) Math.max(minBet, currentPot / 4 * mult);
            cap = chips; // no artificial cap postflop
        }

        if (strength > 0.85) return Math.min(chips, Math.min(cap, (int)(base * 3)));
        if (strength > 0.60) return Math.min(chips, Math.min(cap, (int)(base * 2)));
        return Math.min(chips, Math.min(cap, base));
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
        roundCallCount = 0;
        roundRaiseCount = 0;
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
    public void setCurrentBet(int bet) {
        this.currentBet = bet;
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
        roundCallCount = 0;
        roundRaiseCount = 0;
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
