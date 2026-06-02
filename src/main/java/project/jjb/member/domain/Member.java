package project.jjb.member.domain;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import project.jjb.common.ApiException;

public class Member {

	private final UUID id;
	private final SocialIdentity socialIdentity;
	private final String displayName;
	private final String passwordHash;
	private boolean businessVerified;
	private BusinessOperatingStatus businessOperatingStatus;
	private final EnumSet<MemberRole> roles = EnumSet.noneOf(MemberRole.class);
	private MemberRole activeRole;
	private JobSeekerProfile jobSeekerProfile;
	private OwnerProfile ownerProfile;

	public Member(UUID id, SocialIdentity socialIdentity, String displayName) {
		this(id, socialIdentity, displayName, null);
	}

	public Member(UUID id, SocialIdentity socialIdentity, String displayName, String passwordHash) {
		this.id = id;
		this.socialIdentity = socialIdentity;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
	}

	public static Member restore(
		UUID id,
		SocialIdentity socialIdentity,
		String displayName,
		String passwordHash,
		boolean businessVerified,
		BusinessOperatingStatus businessOperatingStatus,
		Set<MemberRole> roles,
		MemberRole activeRole,
		JobSeekerProfile jobSeekerProfile,
		OwnerProfile ownerProfile
	) {
		Member member = new Member(id, socialIdentity, displayName, passwordHash);
		member.businessVerified = businessVerified;
		member.businessOperatingStatus = businessOperatingStatus;
		member.roles.addAll(roles);
		member.activeRole = activeRole;
		member.jobSeekerProfile = jobSeekerProfile;
		member.ownerProfile = ownerProfile;
		return member;
	}

	public void switchRole(MemberRole role) {
		roles.add(role);
		activeRole = role;
	}

	public void updateJobSeekerProfile(JobSeekerProfile profile) {
		roles.add(MemberRole.JOB_SEEKER);
		jobSeekerProfile = profile;
	}

	public void updateOwnerProfile(OwnerProfile profile) {
		roles.add(MemberRole.OWNER);
		ownerProfile = profile;
	}

	public void completeBusinessVerification(BusinessVerificationResult result) {
		roles.add(MemberRole.OWNER);
		businessVerified = result.verified();
		businessOperatingStatus = result.operatingStatus();
	}

	public void ensureOwnerActionAllowed() {
		if (!roles.contains(MemberRole.OWNER)) {
			throw ApiException.conflict("OWNER_ROLE_REQUIRED", "Owner role is required for this action.");
		}
		if (!businessVerified) {
			throw ApiException.forbidden("BUSINESS_VERIFICATION_REQUIRED", "Business verification is required for this action.");
		}
	}

	public void ensureJobSeekerProfileReady() {
		if (jobSeekerProfile == null) {
			throw ApiException.conflict("JOB_SEEKER_PROFILE_REQUIRED", "Job seeker profile is required for matching.");
		}
	}

	public UUID id() {
		return id;
	}

	public SocialIdentity socialIdentity() {
		return socialIdentity;
	}

	public String displayName() {
		return displayName;
	}

	public String passwordHash() {
		return passwordHash;
	}

	public boolean businessVerified() {
		return businessVerified;
	}

	public BusinessOperatingStatus businessOperatingStatus() {
		return businessOperatingStatus;
	}

	public Set<MemberRole> roles() {
		return roles.isEmpty() ? Set.of() : EnumSet.copyOf(roles);
	}

	public MemberRole activeRole() {
		return activeRole;
	}

	public JobSeekerProfile jobSeekerProfile() {
		return jobSeekerProfile;
	}

	public OwnerProfile ownerProfile() {
		return ownerProfile;
	}
}
