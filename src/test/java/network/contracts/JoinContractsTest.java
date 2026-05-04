package network.contracts;

public class JoinContractsTest {
    public static void main(String[] args) {
        shouldCreateAcceptedDecision();
        shouldCreateRejectedDecision();
        System.out.println("JoinContractsTest: all tests passed");
    }

    private static void shouldCreateAcceptedDecision() {
        JoinRequest request = new JoinRequest("Alice");
        JoinDecision decision = JoinDecision.accepted("p1", request.getProposedName());

        require(decision.isAccepted(), "decision should be accepted");
        require("p1".equals(decision.getPlayerId()), "player id mismatch");
        require("Alice".equals(decision.getDisplayName()), "display name mismatch");
        require(decision.getReasonCode() == null, "reason code should be null");
        require(decision.getDetail() == null, "detail should be null");
    }

    private static void shouldCreateRejectedDecision() {
        JoinDecision decision = JoinDecision.rejected(RejectReason.NAME_TAKEN, "name already in use");

        require(!decision.isAccepted(), "decision should be rejected");
        require(decision.getPlayerId() == null, "player id should be null");
        require(decision.getDisplayName() == null, "display name should be null");
        require(decision.getReasonCode() == RejectReason.NAME_TAKEN, "reason code mismatch");
        require("name already in use".equals(decision.getDetail()), "detail mismatch");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
