package view.fx;

import java.util.List;
import model.AIPlayer;
import model.BettingRound;
import model.Card;

/**
 * Full game-flow demo. Runs on a background thread so the JavaFX
 * Application Thread is never blocked — the UI stays responsive.
 */
public class GameTableDemo {
    public static void main(String[] args) throws Exception {
        JavaFxBootstrap.ensureStarted();

        GameTable table = GameTable.create();
        table.awaitUiReady(5000);
        table.show();

        // Run demo logic on a background thread.
        // UI calls go through Platform.runLater so the FX thread stays free.
        new Thread(() -> runDemo(table)).start();

        table.awaitClose();
    }

    private static void runDemo(GameTable table) {
        log("ROYAL POKER — Full Game Flow Demo");
        log("Close the window (ESC) to exit.");

        // ═══ SETUP ═══
        ui(table, () -> {
            table.updatePlayerInfo("Emaq", 10000, "D", false);
            table.updateAIPlayers(List.of(
                ai("Sofia",  8000, AIPlayer.Role.SMALL_BLIND, false),
                ai("Carlos", 12000, AIPlayer.Role.BIG_BLIND,   false),
                ai("Lucia",   5000, AIPlayer.Role.NONE,        false)
            ));
        });
        log("Setup: 4 players, blinds posted");
        sleep(1500);

        // ═══ PRE-FLOP ═══
        ui(table, () -> {
            table.updatePot(300);
            table.setActionLog(List.of("Sofia: SB 100", "Carlos: BB 200"));
        });
        ui(table, () -> table.dealPlayerHand(List.of(
            new Card("A", "\u2660"), new Card("K", "\u2660"))));
        log("Pre-flop: A\u2660 K\u2660");
        sleep(1500);

        ui(table, () -> table.showBettingButtons(
            new BettingRound(BettingRound.Phase.PREFLOP, 300, 200, 100),
            0, false, a -> log("  Bet: " + a)));
        log("Pre-flop: betting panel (8 sec)");
        sleep(8000);

        // ═══ FLOP ═══
        ui(table, () -> {
            table.updatePot(900);
            table.setActionLog(List.of(
                "Sofia: SB 100", "Carlos: BB 200",
                "Tu: CALL 200", "Sofia: CALL 200"));
        });
        ui(table, () -> table.dealCommunityCards(List.of(
            new Card("Q", "\u2660"), new Card("J", "\u2660"), new Card("10", "\u2660"))));
        log("Flop: Q\u2660 J\u2660 10\u2660");
        sleep(2000);

        ui(table, () -> table.showBettingButtons(
            new BettingRound(BettingRound.Phase.FLOP, 900, 0, 100),
            0, false, a -> log("  Bet: " + a)));
        log("Flop: betting panel (6 sec)");
        sleep(6000);

        // ═══ TURN ═══
        ui(table, () -> {
            table.updatePot(1500);
            table.setActionLog(List.of(
                "Tu: CHECK", "Sofia: BET 300", "Carlos: CALL 300", "Tu: CALL 300"));
        });
        ui(table, () -> table.dealCommunityCards(List.of(
            new Card("Q", "\u2660"), new Card("J", "\u2660"),
            new Card("10", "\u2660"), new Card("9", "\u2660"))));
        log("Turn: 9\u2660");
        sleep(2000);

        ui(table, () -> table.showBettingButtons(
            new BettingRound(BettingRound.Phase.TURN, 1500, 0, 100),
            0, false, a -> log("  Bet: " + a)));
        log("Turn: betting panel (6 sec)");
        sleep(6000);

        // ═══ RIVER ═══
        ui(table, () -> {
            table.updatePot(2700);
            table.setActionLog(List.of(
                "Tu: BET 500", "Sofia: RAISE 1000", "Carlos: FOLD", "Tu: CALL 1000"));
        });
        ui(table, () -> table.dealCommunityCards(List.of(
            new Card("Q", "\u2660"), new Card("J", "\u2660"),
            new Card("10", "\u2660"), new Card("9", "\u2660"),
            new Card("A", "\u2665"))));
        log("River: A\u2665 — royal flush!");
        sleep(2000);

        ui(table, () -> table.showBettingButtons(
            new BettingRound(BettingRound.Phase.RIVER, 2700, 0, 100),
            0, false, a -> log("  Bet: " + a)));
        log("River: betting panel (6 sec)");
        sleep(6000);

        // ═══ SHOWDOWN ═══
        ui(table, () -> {
            table.updatePot(5200);
            table.setActionLog(List.of(
                "Sofia: SHOW Q\u2663 J\u2663 (two pair)",
                "Tu: SHOW A\u2660 K\u2660 (ROYAL FLUSH!)"));
        });
        log("Showdown: Royal Flush!");
        sleep(2500);

        ui(table, () -> table.showResult("ROYAL FLUSH +5,200", true));
        sleep(3000);

        // ═══ GAME OVER ═══
        ui(table, () -> {
            table.updatePlayerInfo("Emaq", 15200, "D", false);
            table.updateAIPlayers(List.of(
                ai("Sofia",  7000, AIPlayer.Role.SMALL_BLIND, false),
                ai("Carlos", 11000, AIPlayer.Role.BIG_BLIND,   false),
                ai("Lucia",   5000, AIPlayer.Role.NONE,        false)
            ));
            table.updatePot(0);
            table.setActionLog(List.of("Emaq: GANADOR +5,200 \u2660"));
        });

        log("Game Over: win (5 sec)");
        ui(table, () -> table.showGameOver(15200));
        sleep(5000);

        log("Game Over: bankrupt (5 sec)");
        ui(table, () -> table.showGameOver(0));
        sleep(5000);

        ui(table, table::hideGameOver);
        log("Done. Window stays open.");
    }

    /** Dispatch a UI call to the JavaFX Application Thread. */
    private static void ui(GameTable table, Runnable action) {
        javafx.application.Platform.runLater(action);
    }

    private static AIPlayer ai(String name, int chips,
                                AIPlayer.Role role, boolean folded) {
        AIPlayer p = new AIPlayer(name, chips);
        p.setRole(role);
        if (folded) p.setFolded(true);
        return p;
    }

    private static void log(String msg) {
        System.out.println("  " + msg);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
