package network.protocol;

import org.json.JSONObject;

public class MessageCodecTest {

    public static void main(String[] args) throws Exception {
        shouldRoundTripEnvelope();
        shouldMapJoinDecision();
        System.out.println("MessageCodecTest: all tests passed");
    }

    private static void shouldRoundTripEnvelope() throws Exception {
        LanEnvelope original = new LanEnvelope(
            LanMessageType.JOIN_REQUEST,
            JoinPayloads.joinRequest("Alice")
        );
        JSONObject json = original.toJson();
        LanEnvelope parsed = LanEnvelope.fromJson(json);
        require(parsed.getType() == LanMessageType.JOIN_REQUEST, "type should survive round trip");
        require("Alice".equals(parsed.getPayload().getString("proposedName")), "payload should survive");
    }

    private static void shouldMapJoinDecision() {
        var accepted = network.contracts.JoinDecision.accepted("id-1", "Bob");
        JSONObject json = JoinPayloads.joinResponse(accepted);
        var parsed = JoinPayloads.joinDecisionFromJson(json);
        require(parsed.isAccepted(), "accepted decision");
        require("id-1".equals(parsed.getPlayerId()), "player id");
        require("Bob".equals(parsed.getDisplayName()), "display name");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
