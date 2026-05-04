package model;

/**
 * Representa el estado de una ronda de apuestas.
 * El GameController la usa para saber qué acciones son válidas,
 * cuánto hay que igualar y cuánto hay en el bote.
 */
public class BettingRound {

    public enum Action { CHECK, BET, CALL, RAISE, FOLD, ALL_IN }
    public enum Phase  { PREFLOP, FLOP, TURN, RIVER }

    private final Phase phase;
    private int pot;
    private int currentBet;   // apuesta más alta de la ronda
    private final int bigBlind;
    private int minRaise;

    public BettingRound(Phase phase, int pot, int currentBet, int bigBlind) {
        this.phase      = phase;
        this.pot        = pot;
        this.currentBet = currentBet;
        this.bigBlind   = bigBlind;
        this.minRaise   = bigBlind;
    }

    // ── Acciones válidas para el jugador humano ───────────────────────────────

    /**
     * ¿Puede hacer check?
     * Sí cuando el jugador ya igualó la apuesta activa de la ronda.
     * En preflop, esto conserva la opción del BB cuando nadie subió
     * (playerCurrentBet == currentBet == bigBlind).
     */
    public boolean canCheck(int playerCurrentBet) {
        if (currentBet == 0) {
            return true;
        }

        return playerCurrentBet >= currentBet;
    }

    /**
     * ¿Puede apostar (bet)? Solo si nadie ha apostado aún (currentBet == 0).
     * Si ya hay apuesta, la acción es RAISE, no BET.
     */
    public boolean canBet() {
        return currentBet == 0;
    }

    /** ¿Cuánto necesita poner para igualar? */
    public int callAmount(int playerCurrentBet) {
        return Math.max(0, currentBet - playerCurrentBet);
    }

    /** ¿Cuánto es el mínimo para subir? */
    public int minRaiseAmount() {
        return currentBet + minRaise;
    }

    // ── Actualizaciones de estado ─────────────────────────────────────────────

    public void playerBets(int amount) {
        if (amount > currentBet) {
            minRaise   = amount - currentBet;
            currentBet = amount;
        }
        pot += amount;
    }

    /** Fuerza el currentBet al valor que resultó de las apuestas de la IA. */
    public void forceCurrentBet(int bet) {
        if (bet > currentBet) {
            minRaise   = bet - currentBet;
            currentBet = bet;
        }
    }

    public void addToPot(int amount) {
        pot += amount;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Phase getPhase()      { return phase; }
    public int   getPot()        { return pot; }
    public int   getCurrentBet() { return currentBet; }
    public int   getBigBlind()   { return bigBlind; }
}
