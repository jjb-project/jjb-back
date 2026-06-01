package project.jjb.member.domain;

public record VerificationSummary(
	boolean phoneVerified,
	boolean businessVerified,
	BusinessOperatingStatus businessOperatingStatus
) {
}
