package network.validation;

import network.contracts.RejectReason;

public class NameValidatorTest {
    public static void main(String[] args) {
        shouldAcceptValidLetterOnlyName();
        shouldNormalizeTrimmedNameWithLocaleRootLowercase();
        shouldProvideSameNormalizedNameForCaseInsensitiveDuplicateBasis();
        shouldRejectInvalidLength();
        shouldRejectInvalidCharacters();
        System.out.println("NameValidatorTest: all tests passed");
    }

    private static void shouldAcceptValidLetterOnlyName() {
        NameValidator validator = new NameValidator();

        NameValidationResult result = validator.validate("Alice");

        require(result.isValid(), "expected valid result");
        require("alice".equals(result.getNormalizedName()), "expected normalized lowercase name");
        require(result.getReasonCode() == null, "reason code should be null");
        require(result.getDetail() == null, "detail should be null");
    }

    private static void shouldNormalizeTrimmedNameWithLocaleRootLowercase() {
        NameValidator validator = new NameValidator();

        NameValidationResult result = validator.validate("  ALICE  ");

        require(result.isValid(), "expected valid result for trimmed input");
        require("alice".equals(result.getNormalizedName()), "normalization should trim and lowercase");
    }

    private static void shouldProvideSameNormalizedNameForCaseInsensitiveDuplicateBasis() {
        NameValidator validator = new NameValidator();

        NameValidationResult mixedCase = validator.validate("Juan");
        NameValidationResult lowerCase = validator.validate("juan");

        require(mixedCase.isValid(), "expected mixed-case name to be valid");
        require(lowerCase.isValid(), "expected lower-case name to be valid");
        require(
            mixedCase.getNormalizedName().equals(lowerCase.getNormalizedName()),
            "normalized names should be equal for duplicate checks"
        );
    }

    private static void shouldRejectInvalidLength() {
        NameValidator validator = new NameValidator();

        NameValidationResult tooShort = validator.validate("Al");
        NameValidationResult tooLong = validator.validate("abcdefghijklmnopq");

        require(!tooShort.isValid(), "expected too-short name rejection");
        require(!tooLong.isValid(), "expected too-long name rejection");
        require(tooShort.getReasonCode() == RejectReason.INVALID_FORMAT, "unexpected reason for short name");
        require(tooLong.getReasonCode() == RejectReason.INVALID_FORMAT, "unexpected reason for long name");
    }

    private static void shouldRejectInvalidCharacters() {
        NameValidator validator = new NameValidator();

        NameValidationResult withDigit = validator.validate("Alice1");
        NameValidationResult withSymbol = validator.validate("Alice_");

        require(!withDigit.isValid(), "expected digit rejection");
        require(!withSymbol.isValid(), "expected symbol rejection");
        require(withDigit.getReasonCode() == RejectReason.INVALID_FORMAT, "unexpected reason code");
        require(withSymbol.getReasonCode() == RejectReason.INVALID_FORMAT, "unexpected reason code");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
