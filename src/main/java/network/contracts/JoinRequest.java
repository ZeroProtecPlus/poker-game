package network.contracts;

public class JoinRequest {
    private final String proposedName;

    public JoinRequest(String proposedName) {
        this.proposedName = proposedName;
    }

    public String getProposedName() {
        return proposedName;
    }
}
