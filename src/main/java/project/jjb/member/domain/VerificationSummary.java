package project.jjb.member.domain;

public record VerificationSummary(
	boolean businessVerified,
	BusinessOperatingStatus businessOperatingStatus
) {
}
