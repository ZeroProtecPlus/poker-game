package controller;

import model.*;
import view.GameView;
import java.util.ArrayList;
import java.util.List;

public class GameController {

    private PokerGame pokerGame;
    private String userNamePlayer;
    private User newPlayer;
    private final GameView newGame;

    public GameController() {
        this(new GameView());
    }

    protected GameController(GameView gameView) {
        this.newGame = gameView;
    }

    public void createNewPlayer() {
        userNamePlayer = newGame.getUserName();
        this.newPlayer = new User(userNamePlayer);
    }

    public void viewUserChips() {
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    // =========================================================================
    //  FLUJO PRINCIPAL
    // =========================================================================

    public void createNewGame() {
        while (newPlayer.getNumbChips() > 0) {
            playOneHand();

            // Preguntar si quiere seguir jugando
            boolean continuar = newGame.askPlayAgain(newPlayer.getNumbChips());
            if (!continuar) break;
        }
        newGame.showGameOver(newPlayer.getNumbChips());
    }

    private void playOneHand() {
        this.pokerGame = new PokerGame(newPlayer);
        pokerGame.startNewRound();

        ArrayList<Card> manoJugador = pokerGame.getPlayerHand();
        newGame.showPlayerHand(manoJugador);
        newGame.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        newGame.showPot(pokerGame.getPot());

        // ── PREFLOP ──────────────────────────────────────────────────────────
        runBettingPhase(BettingRound.Phase.PREFLOP);
        if (allFolded()) { endRound(); return; }

        // ── FLOP ─────────────────────────────────────────────────────────────
        pokerGame.dealFlop();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador);
        runBettingPhase(BettingRound.Phase.FLOP);
        if (allFolded()) { endRound(); return; }

        // ── TURN ─────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador);
        runBettingPhase(BettingRound.Phase.TURN);
        if (allFolded()) { endRound(); return; }

        // ── RIVER ────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador);
        runBettingPhase(BettingRound.Phase.RIVER);

        // ── SHOWDOWN ─────────────────────────────────────────────────────────
        endRound();
    }

    // =========================================================================
    //  RONDA DE APUESTAS
    // =========================================================================

    private void runBettingPhase(BettingRound.Phase phase) {
        pokerGame.resetRoundBets();
        BettingRound round = pokerGame.createBettingRound(phase);

        boolean humanFolded = newPlayer.isFolded();
        int maxIterations   = 6;

        while (maxIterations-- > 0) {
            humanFolded = newPlayer.isFolded();
            int highBetBefore = round.getCurrentBet();

            BettingRound.Action action = null;
            int humanAmount = 0;

            boolean shouldWaitForHumanAction = !pokerGame.isPlayerAllIn() && !humanFolded;
            if (shouldWaitForHumanAction) {
                action = newGame.waitForPlayerAction(round, pokerGame.getPlayerCurrentBet());
                if (action == BettingRound.Action.BET) {
                    humanAmount = newGame.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                } else if (action == BettingRound.Action.CALL) {
                    humanAmount = round.callAmount(pokerGame.getPlayerCurrentBet());
                } else if (action == BettingRound.Action.RAISE) {
                    int minRaise = round.minRaiseAmount();
                    humanAmount = newGame.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                }
            }

            PokerGame.AIBettingResult result = pokerGame.runUnifiedBettingRound(round.getCurrentBet(), action, humanAmount);
            newGame.showAIActions(result.log);

            boolean wasHumanFolded = humanFolded;
            humanFolded = result.humanFolded;
            if (humanFolded && !wasHumanFolded) {
                newGame.showPlayerFolded();
            }

            // Sincronizar BettingRound con la apuesta más alta que hizo la IA
            if (result.highBet > round.getCurrentBet()) {
                round.forceCurrentBet(result.highBet);
            }

            newGame.showPot(pokerGame.getPot());
            newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());

            if (allFoldedExceptOne(humanFolded)) break;

            // Si nadie subió en esta iteración, la ronda terminó
            if (round.getCurrentBet() == highBetBefore) break;
        }

        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    private void applyHumanAction(BettingRound.Action action, BettingRound round) {
        switch (action) {
            case CHECK  -> pokerGame.humanCheck();
            case BET    -> {
                int amount = newGame.getPlayerBetAmount(round.getBigBlind(), newPlayer.getNumbChips());
                pokerGame.humanBet(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case CALL   -> {
                int call = round.callAmount(pokerGame.getPlayerCurrentBet());
                pokerGame.humanCall(call);
            }
            case RAISE  -> {
                int minRaise = round.minRaiseAmount();
                int amount   = newGame.getPlayerBetAmount(minRaise, newPlayer.getNumbChips());
                pokerGame.humanRaise(amount);
                round.playerBets(pokerGame.getPlayerCurrentBet());
            }
            case ALL_IN -> pokerGame.humanAllIn();
            default     -> {}
        }
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    /** True si solo queda un jugador activo en total (incluyendo al humano). */
    private boolean allFoldedExceptOne(boolean humanFolded) {
        long activeAI = pokerGame.getAIPlayers().stream().filter(ai -> !ai.isFolded()).count();
        if (humanFolded) return activeAI <= 1;
        return activeAI == 0;
    }

    // =========================================================================
    //  FIN DE RONDA
    // =========================================================================

    private void endRound() {
        Player winner = pokerGame.determineWinnerPlayer();
        PokerGame.HandRank bestHand = pokerGame.evaluateBestHand();
        int pot = pokerGame.getPot();

        boolean humanWon = winner instanceof User;
        pokerGame.awardPotTo(winner);

        newGame.showResult(
            pokerGame.getCommunityCards(),
            pokerGame.getPlayerHand(),
            bestHand,
            pot,
            humanWon,
            humanWon ? null : winner.getName()
        );

        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    /** Si todas las IAs se retiraron, el humano gana el bote inmediatamente. */
    private boolean allFolded() {
        long active = pokerGame.getAIPlayers().stream().filter(ai -> !ai.isFolded()).count();
        if (active == 0) {
            pokerGame.awardPotToPlayer();
            newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
            newGame.showPot(0);
            return true;
        }
        return false;
    }
}
