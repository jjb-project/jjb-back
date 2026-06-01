package project.jjb.member.domain;

public record BusinessVerificationResult(
	boolean verified,
	BusinessOperatingStatus operatingStatus
) {
}
