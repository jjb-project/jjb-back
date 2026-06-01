package project.jjb.member.domain;

public record PhoneVerificationResult(
	boolean verified,
	String normalizedPhoneNumber
) {
}
