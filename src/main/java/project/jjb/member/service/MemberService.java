package project.jjb.member.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.common.ApiException;
import project.jjb.member.domain.BusinessVerificationCommand;
import project.jjb.member.domain.BusinessVerificationResult;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.Member;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.domain.SocialIdentity;
import project.jjb.member.repository.MemberRepository;

@Service
public class MemberService {

	private final MemberRepository memberRepository;
	private final SocialIdentityPort socialIdentityPort;
	private final BusinessVerificationPort businessVerificationPort;
	private final PasswordEncoder passwordEncoder;

	public MemberService(
		MemberRepository memberRepository,
		SocialIdentityPort socialIdentityPort,
		BusinessVerificationPort businessVerificationPort,
		PasswordEncoder passwordEncoder
	) {
		this.memberRepository = memberRepository;
		this.socialIdentityPort = socialIdentityPort;
		this.businessVerificationPort = businessVerificationPort;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public MemberSnapshot createMember(String socialProvider, String socialSubject, String displayName) {
		SocialIdentity socialIdentity = socialIdentityPort.resolve(socialProvider, socialSubject);
		Member member = memberRepository.findBySocialIdentity(socialIdentity)
			.orElseGet(() -> memberRepository.save(new Member(UUID.randomUUID(), socialIdentity, displayName)));
		return MemberSnapshot.from(member);
	}

	@Transactional
	public MemberSnapshot registerLocalMember(String username, String password, String displayName) {
		String normalizedUsername = normalizeLocalIdentifier(username);
		validatePassword(password);
		String normalizedName = isBlank(displayName)
			? normalizedUsername
			: displayName.trim();
		SocialIdentity socialIdentity = socialIdentityPort.resolve("local", normalizedUsername);
		memberRepository.findBySocialIdentity(socialIdentity)
			.ifPresent(member -> {
				throw ApiException.conflict("LOCAL_ACCOUNT_ALREADY_EXISTS", "이미 가입된 아이디입니다.");
			});
		Member member = new Member(
			UUID.randomUUID(),
			socialIdentity,
			normalizedName,
			passwordEncoder.encode(password)
		);
		return MemberSnapshot.from(memberRepository.save(member));
	}

	@Transactional(readOnly = true)
	public MemberSnapshot loginLocalMember(String username, String password) {
		String normalizedUsername = normalizeLocalIdentifier(username);
		SocialIdentity socialIdentity = socialIdentityPort.resolve("local", normalizedUsername);
		Member member = memberRepository.findBySocialIdentity(socialIdentity)
			.orElseThrow(() -> invalidLocalLogin());
		String passwordHash = member.passwordHash();
		if (isBlank(passwordHash) || !passwordEncoder.matches(password, passwordHash)) {
			throw invalidLocalLogin();
		}
		return MemberSnapshot.from(member);
	}

	@Transactional(readOnly = true)
	public LocalEmailAvailability checkLocalEmailAvailability(String email) {
		String normalizedEmail = normalizeEmail(email);
		SocialIdentity socialIdentity = socialIdentityPort.resolve("local", normalizedEmail);
		boolean available = memberRepository.findBySocialIdentity(socialIdentity).isEmpty();
		String message = available ? "사용 가능한 이메일입니다." : "이미 가입된 이메일입니다.";
		return new LocalEmailAvailability(available, normalizedEmail, message);
	}

	@Transactional(readOnly = true)
	public LocalUsernameAvailability checkLocalUsernameAvailability(String username) {
		String normalizedUsername = normalizeLocalIdentifier(username);
		SocialIdentity socialIdentity = socialIdentityPort.resolve("local", normalizedUsername);
		boolean available = memberRepository.findBySocialIdentity(socialIdentity).isEmpty();
		String message = available ? "사용 가능한 아이디입니다." : "이미 가입된 아이디입니다.";
		return new LocalUsernameAvailability(available, normalizedUsername, message);
	}

	@Transactional(readOnly = true)
	public MemberSnapshot getMember(UUID memberId) {
		return MemberSnapshot.from(requireMember(memberId));
	}

	@Transactional(readOnly = true)
	public List<MemberSnapshot> listJobSeekersWithProfiles() {
		return memberRepository.findJobSeekersWithProfiles().stream()
			.map(MemberSnapshot::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MemberSnapshot> listVerifiedOwnersWithProfiles() {
		return memberRepository.findVerifiedOwnersWithProfiles().stream()
			.map(MemberSnapshot::from)
			.toList();
	}

	@Transactional
	public MemberSnapshot switchRole(UUID memberId, MemberRole role) {
		Member member = requireMember(memberId);
		member.switchRole(role);
		return MemberSnapshot.from(memberRepository.save(member));
	}

	@Transactional
	public MemberSnapshot updateJobSeekerProfile(
		UUID memberId,
		String availableTime,
		String preferredArea,
		int desiredHourlyWage,
		List<String> experiencedIndustries,
		boolean urgentSubstituteAvailable,
		String introduction
	) {
		Member member = requireMember(memberId);
		member.updateJobSeekerProfile(new JobSeekerProfile(
			availableTime,
			preferredArea,
			desiredHourlyWage,
			experiencedIndustries,
			urgentSubstituteAvailable,
			introduction
		));
		return MemberSnapshot.from(memberRepository.save(member));
	}

	@Transactional
	public MemberSnapshot updateOwnerProfile(
		UUID memberId,
		String storeName,
		String storeAddress,
		String businessCategory,
		String storeIntroduction
	) {
		Member member = requireMember(memberId);
		member.updateOwnerProfile(new OwnerProfile(storeName, storeAddress, businessCategory, storeIntroduction));
		return MemberSnapshot.from(memberRepository.save(member));
	}

	@Transactional
	public MemberSnapshot verifyBusiness(
		UUID memberId,
		String businessRegistrationNumber,
		String representativeName,
		LocalDate openingDate
	) {
		Member member = requireMember(memberId);
		BusinessVerificationResult result = businessVerificationPort.verify(
			new BusinessVerificationCommand(businessRegistrationNumber, representativeName, openingDate)
		);
		if (!result.verified()) {
			throw ApiException.badRequest("BUSINESS_VERIFICATION_FAILED", "Business verification failed.");
		}
		member.completeBusinessVerification(result);
		return MemberSnapshot.from(memberRepository.save(member));
	}

	@Transactional(readOnly = true)
	public Member requireOwnerReady(UUID ownerId) {
		Member owner = requireMember(ownerId);
		owner.ensureOwnerActionAllowed();
		return owner;
	}

	@Transactional(readOnly = true)
	public Member requireJobSeekerReady(UUID jobSeekerId) {
		Member jobSeeker = requireMember(jobSeekerId);
		jobSeeker.ensureJobSeekerProfileReady();
		return jobSeeker;
	}

	private Member requireMember(UUID memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "Member was not found."));
	}

	private String normalizeEmail(String email) {
		if (isBlank(email)) {
			throw ApiException.badRequest("INVALID_LOCAL_EMAIL", "이메일을 입력해주세요.");
		}
		String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
		int atIndex = normalizedEmail.indexOf('@');
		if (atIndex < 1 || atIndex == normalizedEmail.length() - 1) {
			throw ApiException.badRequest("INVALID_LOCAL_EMAIL", "올바른 이메일을 입력해주세요.");
		}
		return normalizedEmail;
	}

	private String normalizeLocalIdentifier(String username) {
		if (isBlank(username)) {
			throw ApiException.badRequest("INVALID_LOCAL_USERNAME", "아이디를 입력해주세요.");
		}
		String normalizedUsername = username.trim().toLowerCase(Locale.ROOT);
		if (normalizedUsername.contains("@")) {
			return normalizeEmail(normalizedUsername);
		}
		if (!normalizedUsername.matches("[a-z0-9._-]{4,40}")) {
			throw ApiException.badRequest("INVALID_LOCAL_USERNAME", "아이디는 영문 소문자, 숫자, ., _, - 조합 4~40자로 입력해주세요.");
		}
		return normalizedUsername;
	}

	private void validatePassword(String password) {
		if (password == null || password.length() < 8) {
			throw ApiException.badRequest("INVALID_LOCAL_PASSWORD", "비밀번호는 8자 이상이어야 합니다.");
		}
	}

	private ApiException invalidLocalLogin() {
		return ApiException.badRequest("INVALID_LOCAL_LOGIN", "아이디 또는 비밀번호가 올바르지 않습니다.");
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	public record LocalEmailAvailability(
		boolean available,
		String normalizedEmail,
		String message
	) {
	}

	public record LocalUsernameAvailability(
		boolean available,
		String normalizedUsername,
		String message
	) {
	}
}
