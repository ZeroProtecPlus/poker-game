package network.validation;

import network.contracts.RejectReason;

public class NameValidationResult {
    private final boolean valid;
    private final String normalizedName;
    private final RejectReason reasonCode;
    private final String detail;

    private NameValidationResult(boolean valid, String normalizedName, RejectReason reasonCode, String detail) {
        this.valid = valid;
        this.normalizedName = normalizedName;
        this.reasonCode = reasonCode;
        this.detail = detail;
    }

    public static NameValidationResult valid(String normalizedName) {
        return new NameValidationResult(true, normalizedName, null, null);
    }

    public static NameValidationResult invalid(RejectReason reasonCode, String detail) {
        return new NameValidationResult(false, null, reasonCode, detail);
    }

    public boolean isValid() {
        return valid;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public RejectReason getReasonCode() {
        return reasonCode;
    }

    public String getDetail() {
        return detail;
    }
}
