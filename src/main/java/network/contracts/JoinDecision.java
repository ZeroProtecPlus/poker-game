package network.contracts;

public class JoinDecision {
    private final boolean accepted;
    private final String playerId;
    private final String displayName;
    private final RejectReason reasonCode;
    private final String detail;
    private final int startingChips;

    public JoinDecision(
        boolean accepted,
        String playerId,
        String displayName,
        RejectReason reasonCode,
        String detail
    ) {
        this(accepted, playerId, displayName, reasonCode, detail, 10000);
    }

    public JoinDecision(
        boolean accepted,
        String playerId,
        String displayName,
        RejectReason reasonCode,
        String detail,
        int startingChips
    ) {
        this.accepted = accepted;
        this.playerId = playerId;
        this.displayName = displayName;
        this.reasonCode = reasonCode;
        this.detail = detail;
        this.startingChips = startingChips;
    }

    public static JoinDecision accepted(String playerId, String displayName) {
        return accepted(playerId, displayName, 10000);
    }

    public static JoinDecision accepted(String playerId, String displayName, int startingChips) {
        return new JoinDecision(true, playerId, displayName, null, null, startingChips);
    }

    public static JoinDecision rejected(RejectReason reasonCode, String detail) {
        return new JoinDecision(false, null, null, reasonCode, detail);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public RejectReason getReasonCode() {
        return reasonCode;
    }

    public String getDetail() {
        return detail;
    }

    public int getStartingChips() {
        return startingChips;
    }
}
