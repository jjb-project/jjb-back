package project.jjb.member.repository.persistence;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import project.jjb.member.domain.BusinessOperatingStatus;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.Member;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.domain.SocialIdentity;

@Entity
@Table(
	name = "members",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_members_social_identity",
		columnNames = {"social_provider", "social_subject"}
	)
)
class MemberJpaEntity {

	@Id
	private UUID id;

	@Column(name = "social_provider", nullable = false, length = 40)
	private String socialProvider;

	@Column(name = "social_subject", nullable = false, length = 160)
	private String socialSubject;

	@Column(nullable = false, length = 80)
	private String displayName;

	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@Column(nullable = false)
	private boolean businessVerified;

	@Enumerated(EnumType.STRING)
	@Column(length = 32)
	private BusinessOperatingStatus businessOperatingStatus;

	@Column(nullable = false, length = 80)
	private String roles;

	@Enumerated(EnumType.STRING)
	@Column(length = 32)
	private MemberRole activeRole;

	@Column(length = 120)
	private String availableTime;

	@Column(length = 120)
	private String preferredArea;

	private Integer desiredHourlyWage;

	@Column(length = 300)
	private String experiencedIndustries;

	@Column(nullable = false)
	private boolean urgentSubstituteAvailable;

	@Column(length = 300)
	private String jobSeekerIntroduction;

	@Column(length = 10)
	private String gender;

	@Column(length = 20)
	private String militaryService;

	@Column(length = 40)
	private String education;

	@Column(length = 20)
	private String careerLevel;

	@Column(length = 120)
	private String preferredDays;

	@Column(length = 120)
	private String storeName;

	@Column(length = 240)
	private String storeAddress;

	@Column(length = 80)
	private String businessCategory;

	@Column(length = 300)
	private String storeIntroduction;

	@Column(length = 300)
	private String jobSeekerImageUrl;

	@Column(length = 300)
	private String storeImageUrl;

	protected MemberJpaEntity() {
	}

	static MemberJpaEntity fromDomain(Member member) {
		MemberJpaEntity entity = new MemberJpaEntity();
		entity.id = member.id();
		entity.socialProvider = member.socialIdentity().provider();
		entity.socialSubject = member.socialIdentity().subject();
		entity.displayName = member.displayName();
		entity.passwordHash = member.passwordHash();
		entity.businessVerified = member.businessVerified();
		entity.businessOperatingStatus = member.businessOperatingStatus();
		entity.roles = encodeRoles(member.roles());
		entity.activeRole = member.activeRole();

		JobSeekerProfile jobSeekerProfile = member.jobSeekerProfile();
		if (jobSeekerProfile != null) {
			entity.availableTime = jobSeekerProfile.availableTime();
			entity.preferredArea = jobSeekerProfile.preferredArea();
			entity.desiredHourlyWage = jobSeekerProfile.desiredHourlyWage();
			entity.experiencedIndustries = String.join(",", jobSeekerProfile.experiencedIndustries());
			entity.urgentSubstituteAvailable = jobSeekerProfile.urgentSubstituteAvailable();
			entity.jobSeekerIntroduction = jobSeekerProfile.introduction();
			entity.gender = jobSeekerProfile.gender();
			entity.militaryService = jobSeekerProfile.militaryService();
			entity.education = jobSeekerProfile.education();
			entity.careerLevel = jobSeekerProfile.careerLevel();
			entity.preferredDays = String.join(",", jobSeekerProfile.preferredDays());
			entity.jobSeekerImageUrl = jobSeekerProfile.imageUrl();
		}

		OwnerProfile ownerProfile = member.ownerProfile();
		if (ownerProfile != null) {
			entity.storeName = ownerProfile.storeName();
			entity.storeAddress = ownerProfile.storeAddress();
			entity.businessCategory = ownerProfile.businessCategory();
			entity.storeIntroduction = ownerProfile.storeIntroduction();
			entity.storeImageUrl = ownerProfile.imageUrl();
		}
		return entity;
	}

	Member toDomain() {
		JobSeekerProfile jobSeekerProfile = availableTime == null ? null : new JobSeekerProfile(
			availableTime,
			preferredArea,
			desiredHourlyWage == null ? 0 : desiredHourlyWage,
			decodeList(experiencedIndustries),
			urgentSubstituteAvailable,
			jobSeekerIntroduction,
			gender,
			militaryService,
			education,
			careerLevel,
			decodeList(preferredDays),
			jobSeekerImageUrl
		);
		OwnerProfile ownerProfile = storeName == null ? null : new OwnerProfile(
			storeName,
			storeAddress,
			businessCategory,
			storeIntroduction,
			storeImageUrl
		);
		return Member.restore(
			id,
			new SocialIdentity(socialProvider, socialSubject),
			displayName,
			passwordHash,
			businessVerified,
			businessOperatingStatus,
			decodeRoles(roles),
			activeRole,
			jobSeekerProfile,
			ownerProfile
		);
	}

	private static String encodeRoles(Set<MemberRole> roles) {
		return roles.stream()
			.sorted(Comparator.comparing(Enum::name))
			.map(Enum::name)
			.collect(Collectors.joining(","));
	}

	private static Set<MemberRole> decodeRoles(String roles) {
		if (roles == null || roles.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(roles.split(","))
			.filter(value -> !value.isBlank())
			.map(MemberRole::valueOf)
			.collect(Collectors.toSet());
	}

	private static java.util.List<String> decodeList(String value) {
		if (value == null || value.isBlank()) {
			return java.util.List.of();
		}
		return Arrays.stream(value.split(","))
			.filter(item -> !item.isBlank())
			.toList();
	}
}
