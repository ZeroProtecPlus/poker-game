package controller;

import model.*;
import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.contracts.RejectReason;
import network.host.HostJoinHandler;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;
import view.GameView;
import java.util.ArrayList;
import java.util.List;

public class GameController {

    private PokerGame pokerGame;
    private String playerId;
    private String userNamePlayer;
    private User newPlayer;
    private final GameView newGame;
    private final HostJoinHandler hostJoinHandler;

    public GameController() {
        this(new GameView(), createDefaultHostJoinHandler());
    }

    protected GameController(GameView gameView) {
        this(gameView, createDefaultHostJoinHandler());
    }

    protected GameController(GameView gameView, HostJoinHandler hostJoinHandler) {
        this.newGame = gameView;
        this.hostJoinHandler = hostJoinHandler;
    }

    public void createNewPlayer() {
        while (true) {
            JoinDecision decision = requestJoinAdmission(newGame.getUserName());
            if (decision.isAccepted()) {
                applyAcceptedJoinDecision(decision);
                return;
            }

            newGame.showJoinRejectionMessage(toJoinRejectionMessage(decision));
            if (!newGame.askRetryJoin()) {
                throw new IllegalStateException("Ingreso a la sesion cancelado");
            }
        }
    }

    protected JoinDecision requestJoinAdmission(String proposedName) {
        return hostJoinHandler.handleJoin(new JoinRequest(proposedName));
    }

    private void applyAcceptedJoinDecision(JoinDecision decision) {
        this.playerId = decision.getPlayerId();
        this.userNamePlayer = decision.getDisplayName();
        this.newPlayer = new User(playerId, userNamePlayer);
    }

    private String toJoinRejectionMessage(JoinDecision decision) {
        RejectReason reasonCode = decision.getReasonCode();
        if (reasonCode == RejectReason.NAME_TAKEN) {
            return "Ese nombre ya esta en uso en la sesion. Proba con otro.";
        }
        if (reasonCode == RejectReason.INVALID_FORMAT) {
            return "Nombre invalido. Usa solo letras (3 a 16 caracteres).";
        }
        return "No se pudo unir a la sesion por un error interno. Intenta nuevamente.";
    }

    private static HostJoinHandler createDefaultHostJoinHandler() {
        return new HostJoinHandler(new NameValidator(), new SessionNameRegistry());
    }

    public void viewUserChips() {
        newGame.showUserChips(userNamePlayer, newPlayer.getNumbChips());
    }

    public String getPlayerId() {
        return playerId;
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
        newGame.clearTableForNewHand();
        this.pokerGame = new PokerGame(newPlayer);
        pokerGame.startNewRound();

        ArrayList<Card> manoJugador = pokerGame.getPlayerHand();
        newGame.showPlayerHand(manoJugador);
        newGame.showRoles(pokerGame.getHumanRole(), pokerGame.getAIPlayers());
        newGame.showPot(pokerGame.getPot());

// ── PREFLOP ──────────────────────────────────────────────────────────
        runBettingPhase(BettingRound.Phase.PREFLOP);
        if (allFolded()) return; // allFolded already awards pot - no showdown needed

        // ── FLOP ─────────────────────────────────────────────────────────────
        pokerGame.dealFlop();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador);
        runBettingPhase(BettingRound.Phase.FLOP);
        if (allFolded()) return; // allFolded already awards pot - no showdown needed

        // ── TURN ─────────────────────────────────────────────────────────────
        pokerGame.dealTurnOrRiver();
        newGame.showCommunityCards(pokerGame.getCommunityCards(), manoJugador);
        runBettingPhase(BettingRound.Phase.TURN);
        if (allFolded()) return; // allFolded already awards pot - no showdown needed

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
        ShowdownResult showdownResult = pokerGame.determineShowdownResult();
        int pot = pokerGame.getPot();

        pokerGame.awardPot(showdownResult);

        newGame.showResult(
            pokerGame.getCommunityCards(),
            pokerGame.getPlayerHand(),
            showdownResult,
            pot
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
