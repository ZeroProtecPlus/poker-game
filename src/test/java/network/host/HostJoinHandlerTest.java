package network.host;

import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.contracts.RejectReason;
import network.session.SessionNameRegistry;
import network.validation.NameValidator;

public class HostJoinHandlerTest {

    public static void main(String[] args) {
        shouldAcceptValidUniqueJoinAndCommitName();
        shouldRejectInvalidFormatWithDeterministicReason();
        shouldRejectTakenNameCaseInsensitively();
        shouldReleaseReservationWhenAdmissionFails();
        System.out.println("HostJoinHandlerTest: all tests passed");
    }

    private static void shouldAcceptValidUniqueJoinAndCommitName() {
        SessionNameRegistry registry = new SessionNameRegistry();
        HostJoinHandler handler = new HostJoinHandler(
            new NameValidator(),
            registry,
            (playerId, displayName) -> true,
            () -> "player-001"
        );

        JoinDecision decision = handler.handleJoin(new JoinRequest("  Alice  "));

        require(decision.isAccepted(), "expected accepted join");
        require("player-001".equals(decision.getPlayerId()), "expected generated player id");
        require("Alice".equals(decision.getDisplayName()), "expected trimmed display name");
        require(registry.isCommitted("alice"), "normalized name should be committed");
    }

    private static void shouldRejectInvalidFormatWithDeterministicReason() {
        SessionNameRegistry registry = new SessionNameRegistry();
        HostJoinHandler handler = new HostJoinHandler(new NameValidator(), registry);

        JoinDecision decision = handler.handleJoin(new JoinRequest("a1"));

        require(!decision.isAccepted(), "invalid name should be rejected");
        require(decision.getReasonCode() == RejectReason.INVALID_FORMAT, "expected INVALID_FORMAT reason");
        require(!registry.isCommitted("a1"), "invalid name should never be committed");
    }

    private static void shouldRejectTakenNameCaseInsensitively() {
        SessionNameRegistry registry = new SessionNameRegistry();
        HostJoinHandler handler = new HostJoinHandler(
            new NameValidator(),
            registry,
            (playerId, displayName) -> true,
            new SequentialIdGenerator()
        );

        JoinDecision first = handler.handleJoin(new JoinRequest("Juan"));
        JoinDecision second = handler.handleJoin(new JoinRequest(" juan "));

        require(first.isAccepted(), "first unique join should pass");
        require(!second.isAccepted(), "duplicate normalized name should be rejected");
        require(second.getReasonCode() == RejectReason.NAME_TAKEN, "expected NAME_TAKEN reason");
    }

    private static void shouldReleaseReservationWhenAdmissionFails() {
        SessionNameRegistry registry = new SessionNameRegistry();
        HostJoinHandler failingHandler = new HostJoinHandler(
            new NameValidator(),
            registry,
            (playerId, displayName) -> false,
            () -> "player-x"
        );

        JoinDecision rejected = failingHandler.handleJoin(new JoinRequest("Mateo"));
        require(!rejected.isAccepted(), "failed admission should reject");
        require(rejected.getReasonCode() == RejectReason.INTERNAL_ERROR, "admission failure should map to INTERNAL_ERROR");

        HostJoinHandler retryHandler = new HostJoinHandler(
            new NameValidator(),
            registry,
            (playerId, displayName) -> true,
            () -> "player-y"
        );

        JoinDecision retried = retryHandler.handleJoin(new JoinRequest("Mateo"));
        require(retried.isAccepted(), "name should be reusable after failed admission release");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class SequentialIdGenerator implements HostJoinHandler.PlayerIdGenerator {
        private int next = 1;

        @Override
        public String nextPlayerId() {
            return "player-" + next++;
        }
    }
}
