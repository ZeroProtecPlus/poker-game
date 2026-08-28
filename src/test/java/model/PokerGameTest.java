package model;

public class PokerGameTest {
    public static void main(String[] args) {
        shouldReportAllOpponentsEliminatedWhenAllAIsHaveZeroChips();
        shouldReportAllOpponentsNotEliminatedWhenAIsHaveChips();
        shouldReturnPlayerWithMostChipsAsOverallWinner();
        shouldReturnNullWhenAllPlayersHaveZeroChips();
        System.out.println("PokerGameTest: all tests passed");
    }

    /** REQ-4: 3 AIs with 0 chips → areAllOpponentsEliminated() = true */
    private static void shouldReportAllOpponentsEliminatedWhenAllAIsHaveZeroChips() {
        User user = new User("Human");
        user.setChips(5000);
        PokerGame game = new PokerGame(user);
        // Set all AI opponents to 0 chips
        for (AIPlayer ai : game.getAIPlayers()) {
            ai.setChips(0);
        }
        require(game.areAllOpponentsEliminated(),
            "all AIs at 0 chips → opponents eliminated");
    }

    /** REQ-4: 1 AI with chips → areAllOpponentsEliminated() = false */
    private static void shouldReportAllOpponentsNotEliminatedWhenAIsHaveChips() {
        User user = new User("Human");
        user.setChips(5000);
        PokerGame game = new PokerGame(user);
        // Leave AIs with their default 10000 chips
        require(!game.areAllOpponentsEliminated(),
            "AIs with chips > 0 → opponents NOT eliminated");
    }

    /** REQ-2: Player with highest chips > 0 is returned as overall winner */
    private static void shouldReturnPlayerWithMostChipsAsOverallWinner() {
        User user = new User("Human");
        user.setChips(5000);
        PokerGame game = new PokerGame(user);
        java.util.List<AIPlayer> ais = game.getAIPlayers();
        // Human: 5000, AI0: 3000, AI1: 0, AI2: 0
        ais.get(0).setChips(3000);
        ais.get(1).setChips(0);
        ais.get(2).setChips(0);

        Player winner = game.determineOverallWinner();
        require(winner != null, "should find a winner when players have chips");
        require("Human".equals(winner.getName()),
            "player with most chips (5000) should be winner, got: " +
            (winner != null ? winner.getName() : "null"));
    }

    /** REQ-2 edge case: all-zero returns null */
    private static void shouldReturnNullWhenAllPlayersHaveZeroChips() {
        User user = new User("Human");
        user.setChips(0);
        PokerGame game = new PokerGame(user);
        for (AIPlayer ai : game.getAIPlayers()) {
            ai.setChips(0);
        }
        require(game.determineOverallWinner() == null,
            "all players at 0 chips → no overall winner (null)");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
