package project.jjb.member.domain;

import java.util.Set;
import java.util.UUID;

public record MemberSnapshot(
	UUID id,
	String displayName,
	String socialProvider,
	Set<MemberRole> roles,
	MemberRole activeRole,
	VerificationSummary verification,
	JobSeekerProfile jobSeekerProfile,
	OwnerProfile ownerProfile
) {

	public static MemberSnapshot from(Member member) {
		return new MemberSnapshot(
			member.id(),
			member.displayName(),
			member.socialIdentity().provider(),
			member.roles(),
			member.activeRole(),
			new VerificationSummary(member.businessVerified(), member.businessOperatingStatus()),
			member.jobSeekerProfile(),
			member.ownerProfile()
		);
	}
}
