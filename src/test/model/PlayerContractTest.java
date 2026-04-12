package model;

public class PlayerContractTest {
    public static void main(String[] args) {
        shouldExposeEquivalentContractBehaviorForUserAndAI();
        shouldGuardInvalidBetsForUserAndAI();
        System.out.println("PlayerContractTest: all tests passed");
    }

    private static void shouldExposeEquivalentContractBehaviorForUserAndAI() {
        assertPlayerContractBasics(new User("Alice"), 10000);
        assertPlayerContractBasics(new AIPlayer("Bot-1", 1200), 1200);
    }

    private static void shouldGuardInvalidBetsForUserAndAI() {
        assertBetGuards(new User("Bob"), 10000);
        assertBetGuards(new AIPlayer("Bot-2", 1500), 1500);
    }

    private static void assertPlayerContractBasics(Player player, int startingChips) {
        require(startingChips == player.getChips(), "starting chips mismatch");
        require(player.getCurrentBet() == 0, "current bet should start at zero");
        require(!player.isFolded(), "player should not start folded");
        require(!player.isAllIn(), "player should not start all-in");
        require(player.getHand().isEmpty(), "hand should start empty");

        player.setRole(PlayerRole.DEALER);
        require(player.getPlayerRole() == PlayerRole.DEALER, "role assignment mismatch");

        player.addCard(new Card("A", "S"));
        require(player.getHand().size() == 1, "hand should contain added card");

        int firstBet = player.placeBet(200);
        require(firstBet == 200, "first bet amount mismatch");
        require(player.getCurrentBet() == 200, "current bet should reflect placed bet");
        require(player.getChips() == startingChips - 200, "chips should decrease by bet");

        player.resetRoundBet();
        require(player.getCurrentBet() == 0, "current bet should reset to zero");

        player.clearHand();
        require(player.getHand().isEmpty(), "hand should be empty after clear");
        require(!player.isFolded(), "clear hand should clear folded state");
        require(!player.isAllIn(), "clear hand should clear all-in state");
        require(player.getCurrentBet() == 0, "clear hand should clear current bet");
    }

    private static void assertBetGuards(Player player, int startingChips) {
        int negativeBet = player.placeBet(-10);
        require(negativeBet == 0, "negative amount should be rejected");
        require(player.getChips() == startingChips, "chips should not change on invalid bet");

        int zeroBet = player.placeBet(0);
        require(zeroBet == 0, "zero amount should be rejected");
        require(player.getCurrentBet() == 0, "current bet should not change on zero bet");

        player.setFolded(true);
        int foldedBet = player.placeBet(100);
        require(foldedBet == 0, "folded player should not place bet");
        require(player.getCurrentBet() == 0, "current bet should not change when folded");
        player.setFolded(false);

        player.setAllIn(true);
        int allInBet = player.placeBet(100);
        require(allInBet == 0, "all-in player should not place bet");
        require(player.getCurrentBet() == 0, "current bet should not change when all-in");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
