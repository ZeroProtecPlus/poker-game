package network.host;

import network.contracts.JoinDecision;
import network.contracts.JoinRequest;
import network.contracts.RejectReason;
import network.session.SessionNameRegistry;
import network.validation.NameValidationResult;
import network.validation.NameValidator;

import java.util.Objects;
import java.util.UUID;

public class HostJoinHandler {

    @FunctionalInterface
    public interface PlayerAdmission {
        boolean admit(String playerId, String displayName);
    }

    @FunctionalInterface
    public interface PlayerIdGenerator {
        String nextPlayerId();
    }

    private final NameValidator nameValidator;
    private final SessionNameRegistry sessionNameRegistry;
    private final PlayerAdmission playerAdmission;
    private final PlayerIdGenerator playerIdGenerator;

    public HostJoinHandler(NameValidator nameValidator, SessionNameRegistry sessionNameRegistry) {
        this(nameValidator, sessionNameRegistry, (playerId, displayName) -> true, () -> UUID.randomUUID().toString());
    }

    public HostJoinHandler(
        NameValidator nameValidator,
        SessionNameRegistry sessionNameRegistry,
        PlayerAdmission playerAdmission,
        PlayerIdGenerator playerIdGenerator
    ) {
        this.nameValidator = Objects.requireNonNull(nameValidator, "nameValidator is required");
        this.sessionNameRegistry = Objects.requireNonNull(sessionNameRegistry, "sessionNameRegistry is required");
        this.playerAdmission = Objects.requireNonNull(playerAdmission, "playerAdmission is required");
        this.playerIdGenerator = Objects.requireNonNull(playerIdGenerator, "playerIdGenerator is required");
    }

    public JoinDecision handleJoin(JoinRequest request) {
        if (request == null) {
            return JoinDecision.rejected(RejectReason.INTERNAL_ERROR, "join request is required");
        }

        NameValidationResult validation = nameValidator.validate(request.getProposedName());
        if (!validation.isValid()) {
            return JoinDecision.rejected(validation.getReasonCode(), validation.getDetail());
        }

        String normalizedName = validation.getNormalizedName();
        if (!sessionNameRegistry.reserve(normalizedName)) {
            return JoinDecision.rejected(RejectReason.NAME_TAKEN, "name is already in use");
        }

        boolean committed = false;
        try {
            String playerId = playerIdGenerator.nextPlayerId();
            if (playerId == null || playerId.isBlank()) {
                return JoinDecision.rejected(RejectReason.INTERNAL_ERROR, "failed to assign player identity");
            }

            String displayName = request.getProposedName().trim();
            if (!playerAdmission.admit(playerId, displayName)) {
                return JoinDecision.rejected(RejectReason.INTERNAL_ERROR, "failed to admit player to session");
            }

            if (!sessionNameRegistry.commit(normalizedName)) {
                return JoinDecision.rejected(RejectReason.INTERNAL_ERROR, "failed to commit reserved name");
            }

            committed = true;
            return JoinDecision.accepted(playerId, displayName);
        } catch (RuntimeException ex) {
            return JoinDecision.rejected(RejectReason.INTERNAL_ERROR, "host join processing failed");
        } finally {
            if (!committed) {
                sessionNameRegistry.release(normalizedName);
            }
        }
    }
}
