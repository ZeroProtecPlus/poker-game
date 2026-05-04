package network.validation;

import network.contracts.RejectReason;

import java.util.Locale;

public class NameValidator {
    private static final int MIN_NAME_LENGTH = 3;
    private static final int MAX_NAME_LENGTH = 16;

    public NameValidationResult validate(String proposedName) {
        if (proposedName == null) {
            return NameValidationResult.invalid(RejectReason.INVALID_FORMAT, "name is required");
        }

        String trimmed = proposedName.trim();
        if (trimmed.length() < MIN_NAME_LENGTH || trimmed.length() > MAX_NAME_LENGTH) {
            return NameValidationResult.invalid(
                RejectReason.INVALID_FORMAT,
                "name length must be between " + MIN_NAME_LENGTH + " and " + MAX_NAME_LENGTH
            );
        }

        if (!trimmed.matches("^[A-Za-z]+$")) {
            return NameValidationResult.invalid(
                RejectReason.INVALID_FORMAT,
                "only letters are allowed"
            );
        }

        return NameValidationResult.valid(normalize(trimmed));
    }

    public String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
