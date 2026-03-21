package model;

import java.util.*;

public class PokerGame {

    // ── Constantes — mesa 5/10 ────────────────────────────────────────────────
    public static final int SMALL_BLIND = 5;
    public static final int BIG_BLIND   = 10;

    // ── Ranking de manos ──────────────────────────────────────────────────────
    public enum HandRank {
        HIGH_CARD       ("Carta Alta"),
        ONE_PAIR        ("Par"),
        TWO_PAIR        ("Doble Par"),
        THREE_OF_A_KIND ("Trío"),
        STRAIGHT        ("Escalera"),
        FLUSH           ("Color"),
        FULL_HOUSE      ("Full House"),
        FOUR_OF_A_KIND  ("Póker"),
        STRAIGHT_FLUSH  ("Escalera de Color"),
        ROYAL_FLUSH     ("Escalera Real");

        public final String spanishName;
        HandRank(String name) { this.spanishName = name; }
    }

    // ── Mapa de valores ───────────────────────────────────────────────────────
    private static final Map<String, Integer> RANK_VALUE = new LinkedHashMap<>();
    static {
        RANK_VALUE.put("2",2); RANK_VALUE.put("3",3); RANK_VALUE.put("4",4);
        RANK_VALUE.put("5",5); RANK_VALUE.put("6",6); RANK_VALUE.put("7",7);
        RANK_VALUE.put("8",8); RANK_VALUE.put("9",9); RANK_VALUE.put("10",10);
        RANK_VALUE.put("J",11); RANK_VALUE.put("Q",12); RANK_VALUE.put("K",13);
        RANK_VALUE.put("A",14);
    }

    // ── Estado de la partida ──────────────────────────────────────────────────
    private Deck deck;
    private final User player;
    private final ArrayList<AIPlayer> aiPlayers = new ArrayList<>();

    private final ArrayList<Card> playerHand    = new ArrayList<>();
    private final ArrayList<Card> communityCards = new ArrayList<>();

    private int dealerIndex = 0;  // índice del dealer entre los 4 jugadores (0=human,1-3=IA)
    private int pot         = 0;
    private int playerCurrentBet = 0;

    // =========================================================================
    public PokerGame(User player) {
        this.player = player;
        if (player != null) {
            aiPlayers.add(new AIPlayer("Carlos", 10000));
            aiPlayers.add(new AIPlayer("María",  10000));
            aiPlayers.add(new AIPlayer("Sofía",  10000));
        }
    }

    // =========================================================================
    //  FLUJO DE RONDA
    // =========================================================================

    public void startNewRound() {
        deck = new Deck(); // auto-baraja
        playerHand.clear();
        communityCards.clear();
        pot = 0;
        playerCurrentBet = 0;
        playerAllIn = false;

        for (AIPlayer ai : aiPlayers) ai.clearHand();

        assignRoles();
        dealAllHands();
        postBlinds();
    }

    /**
     * Asigna roles rotando el índice del dealer.
     * Con 4 jugadores: dealer, small blind, big blind, none (en orden).
     */
    private void assignRoles() {
        // Los 4 asientos: 0=human, 1=AI[0], 2=AI[1], 3=AI[2]
        // dealerIndex indica quién es el dealer este turno
        int[] seats = {0, 1, 2, 3};

        for (int i = 0; i < 4; i++) {
            int seat = (dealerIndex + i) % 4;
            AIPlayer.Role role = switch (i) {
                case 0 -> AIPlayer.Role.DEALER;
                case 1 -> AIPlayer.Role.SMALL_BLIND;
                case 2 -> AIPlayer.Role.BIG_BLIND;
                default -> AIPlayer.Role.NONE;
            };
            if (seat == 0) {
                // El jugador humano no tiene campo Role propio — lo guardamos aparte
                humanRole = role;
            } else {
                aiPlayers.get(seat - 1).setRole(role);
            }
        }

        // Avanzar dealer para la próxima ronda
        dealerIndex = (dealerIndex + 1) % 4;
    }

    private AIPlayer.Role humanRole = AIPlayer.Role.NONE;

    public AIPlayer.Role getHumanRole() { return humanRole; }

    private void dealAllHands() {
        // 2 cartas al jugador humano
        playerHand.add(deck.dealCard());
        playerHand.add(deck.dealCard());
        // 2 cartas a cada IA
        for (AIPlayer ai : aiPlayers) {
            ai.addCard(deck.dealCard());
            ai.addCard(deck.dealCard());
        }
    }

    /**
     * Cobra los blinds automáticamente al inicio de cada mano.
     * Mesa 5/10: SB pone 5, BB pone 10.
     */
    private void postBlinds() {
        for (AIPlayer ai : aiPlayers) {
            if (ai.getRole() == AIPlayer.Role.SMALL_BLIND) {
                pot += ai.placeBet(SMALL_BLIND);
            } else if (ai.getRole() == AIPlayer.Role.BIG_BLIND) {
                pot += ai.placeBet(BIG_BLIND);
            }
        }
        if (humanRole == AIPlayer.Role.SMALL_BLIND) {
            int paid = Math.min(SMALL_BLIND, player.getNumbChips());
            player.setNumbChips(player.getNumbChips() - paid);
            pot += paid;
            playerCurrentBet = paid;
        } else if (humanRole == AIPlayer.Role.BIG_BLIND) {
            int paid = Math.min(BIG_BLIND, player.getNumbChips());
            player.setNumbChips(player.getNumbChips() - paid);
            pot += paid;
            playerCurrentBet = paid;
        }
    }

    // ── Fases comunitarias ────────────────────────────────────────────────────

    public void dealFlop() {
        burnCard();
        for (int i = 0; i < 3; i++) communityCards.add(deck.dealCard());
    }

    public void dealTurnOrRiver() {
        burnCard();
        communityCards.add(deck.dealCard());
    }

    private void burnCard() { deck.dealCard(); }

    // =========================================================================
    //  APUESTAS — JUGADOR HUMANO
    // =========================================================================

    public BettingRound createBettingRound(BettingRound.Phase phase) {
        // currentBet = la apuesta más alta entre todos los jugadores activos
        int highBet = playerCurrentBet;
        for (AIPlayer ai : aiPlayers) {
            if (!ai.isFolded()) highBet = Math.max(highBet, ai.getCurrentBet());
        }
        return new BettingRound(phase, pot, highBet, BIG_BLIND);
    }

    private boolean playerAllIn = false;

    public boolean isPlayerAllIn() { return playerAllIn; }

    /** El jugador humano hace check. */
    public void humanCheck() { /* no mueve fichas */ }

    /** El jugador humano apuesta. */
    public void humanBet(int amount) {
        int actual = Math.min(amount, player.getNumbChips());
        player.setNumbChips(player.getNumbChips() - actual);
        playerCurrentBet += actual;
        pot += actual;
        if (player.getNumbChips() == 0) playerAllIn = true;
    }

    /** El jugador humano iguala. */
    public int humanCall(int callAmount) {
        int actual = Math.min(callAmount, player.getNumbChips());
        player.setNumbChips(player.getNumbChips() - actual);
        playerCurrentBet += actual;
        pot += actual;
        if (player.getNumbChips() == 0) playerAllIn = true;
        return actual;
    }

    /** El jugador humano sube. */
    public void humanRaise(int totalAmount) {
        int extra  = Math.max(0, totalAmount - playerCurrentBet);
        int actual = Math.min(extra, player.getNumbChips());
        player.setNumbChips(player.getNumbChips() - actual);
        playerCurrentBet += actual;
        pot += actual;
        if (player.getNumbChips() == 0) playerAllIn = true;
    }

    /** El jugador humano va all-in. */
    public void humanAllIn() {
        if (!playerAllIn) humanBet(player.getNumbChips());
    }

    // =========================================================================
    //  APUESTAS — IA
    // =========================================================================

    /**
     * Ejecuta la ronda de apuestas de todas las IAs.
     * Devuelve un log de acciones para mostrar en la UI.
     */
    /**
     * Ejecuta la ronda de apuestas de todas las IAs.
     * @return par [log, highBet final] — el controlador usa el highBet para actualizar BettingRound
     */
    public AIBettingResult runAIBettingRound(int currentHighBet) {
        List<String> log = new ArrayList<>();

        for (AIPlayer ai : aiPlayers) {
            if (ai.isFolded() || ai.isAllIn()) continue;

            int callAmount = Math.max(0, currentHighBet - ai.getCurrentBet());
            BettingRound.Action action = ai.decide(callAmount, pot, communityCards, ai.getRole());

            switch (action) {
                case FOLD  -> { ai.setFolded(true); log.add(ai.getName() + " se retira"); }
                case CHECK -> { log.add(ai.getName() + " pasa"); }
                case CALL  -> {
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

    /** Resultado de una ronda de apuestas de IA: log de acciones + apuesta más alta resultante. */
    public static class AIBettingResult {
        public final List<String> log;
        public final int highBet;
        public AIBettingResult(List<String> log, int highBet) {
            this.log     = log;
            this.highBet = highBet;
        }
    }

    /** Resetea las apuestas de ronda para todos los jugadores. */
    public void resetRoundBets() {
        playerCurrentBet = 0;
        for (AIPlayer ai : aiPlayers) ai.resetRoundBet();
    }

    // =========================================================================
    //  EVALUACIÓN
    // =========================================================================

    public HandRank evaluateBestHand() {
        ArrayList<Card> all = new ArrayList<>(playerHand);
        all.addAll(communityCards);
        return evaluateCards(all);
    }

    /** Público para que AIPlayer pueda usarlo sin instanciar PokerGame completo. */
    public HandRank evaluateCards(List<Card> cards) {
        if (cards.size() < 5) return HandRank.HIGH_CARD;
        List<List<Card>> combos = combinations(new ArrayList<>(cards), 5);
        HandRank best = HandRank.HIGH_CARD;
        for (List<Card> combo : combos) {
            HandRank r = evaluateFive(combo);
            if (r.ordinal() > best.ordinal()) best = r;
        }
        return best;
    }

    private HandRank evaluateFive(List<Card> five) {
        boolean flush    = isFlush(five);
        boolean straight = isStraight(five);
        if (flush && straight) {
            List<Integer> v = sortedValues(five);
            return (v.get(4) == 14 && v.get(0) == 10) ? HandRank.ROYAL_FLUSH : HandRank.STRAIGHT_FLUSH;
        }
        if (hasNOfAKind(five, 4)) return HandRank.FOUR_OF_A_KIND;
        if (isFullHouse(five))    return HandRank.FULL_HOUSE;
        if (flush)                return HandRank.FLUSH;
        if (straight)             return HandRank.STRAIGHT;
        if (hasNOfAKind(five, 3)) return HandRank.THREE_OF_A_KIND;
        if (isTwoPair(five))      return HandRank.TWO_PAIR;
        if (hasNOfAKind(five, 2)) return HandRank.ONE_PAIR;
        return HandRank.HIGH_CARD;
    }

    private boolean isFlush(List<Card> c) {
        String s = c.get(0).getSuit();
        return c.stream().allMatch(x -> x.getSuit().equals(s));
    }

    private boolean isStraight(List<Card> c) {
        List<Integer> v = sortedValues(c);
        if (v.equals(Arrays.asList(2,3,4,5,14))) return true;
        for (int i = 1; i < v.size(); i++) if (v.get(i) != v.get(i-1)+1) return false;
        return true;
    }

    private boolean hasNOfAKind(List<Card> c, int n) { return rankCounts(c).containsValue(n); }

    private boolean isFullHouse(List<Card> c) {
        Map<String,Integer> m = rankCounts(c);
        return m.containsValue(3) && m.containsValue(2);
    }

    private boolean isTwoPair(List<Card> c) {
        return rankCounts(c).values().stream().filter(v -> v == 2).count() == 2;
    }

    private Map<String,Integer> rankCounts(List<Card> c) {
        Map<String,Integer> m = new HashMap<>();
        for (Card x : c) m.put(x.getRank(), m.getOrDefault(x.getRank(),0)+1);
        return m;
    }

    private List<Integer> sortedValues(List<Card> c) {
        List<Integer> v = new ArrayList<>();
        for (Card x : c) v.add(RANK_VALUE.get(x.getRank()));
        Collections.sort(v);
        return v;
    }

    private <T> List<List<T>> combinations(List<T> list, int k) {
        List<List<T>> result = new ArrayList<>();
        combinationsHelper(list, k, 0, new ArrayList<>(), result);
        return result;
    }

    private <T> void combinationsHelper(List<T> list, int k, int start, List<T> cur, List<List<T>> res) {
        if (cur.size() == k) { res.add(new ArrayList<>(cur)); return; }
        for (int i = start; i < list.size(); i++) {
            cur.add(list.get(i));
            combinationsHelper(list, k, i+1, cur, res);
            cur.remove(cur.size()-1);
        }
    }

    // =========================================================================
    //  DISTRIBUCIÓN DEL BOTE
    // =========================================================================

    /**
     * Acredita el bote completo al jugador humano (ganó la mano).
     */
    public void awardPotToPlayer() {
        player.setNumbChips(player.getNumbChips() + pot);
        pot = 0;
    }

    /**
     * Acredita el bote completo a una IA (ganó la mano).
     */
    public void awardPotToAI(AIPlayer winner) {
        winner.receivePot(pot);
        pot = 0;
    }

    /**
     * Determina el ganador en el showdown comparando las manos de todos
     * los jugadores activos (no foldeados).
     * Devuelve null si el humano gana, o el AIPlayer ganador.
     */
    public AIPlayer determineWinner() {
        // Evaluar mano del humano
        ArrayList<Card> humanCards = new ArrayList<>(playerHand);
        humanCards.addAll(communityCards);
        HandRank humanRank = evaluateCards(humanCards);

        AIPlayer bestAI     = null;
        HandRank bestAIRank = HandRank.HIGH_CARD;

        for (AIPlayer ai : aiPlayers) {
            if (ai.isFolded()) continue;
            ArrayList<Card> aiCards = new ArrayList<>(ai.getHand());
            aiCards.addAll(communityCards);
            HandRank aiRank = evaluateCards(aiCards);
            if (aiRank.ordinal() > bestAIRank.ordinal()) {
                bestAIRank = aiRank;
                bestAI     = ai;
            }
        }

        // El humano gana si su mano es >= mejor IA activa
        if (bestAI == null || humanRank.ordinal() >= bestAIRank.ordinal()) {
            return null; // null = humano gana
        }
        return bestAI;
    }

    // =========================================================================
    //  GETTERS
    // =========================================================================

    public ArrayList<Card> getPlayerHand()     { return new ArrayList<>(playerHand); }
    public ArrayList<Card> getCommunityCards()  { return new ArrayList<>(communityCards); }
    public List<AIPlayer>  getAIPlayers()       { return Collections.unmodifiableList(aiPlayers); }
    public int             getPot()             { return pot; }
    public int             getPlayerCurrentBet(){ return playerCurrentBet; }
}