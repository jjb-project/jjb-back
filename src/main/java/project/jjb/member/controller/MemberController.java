package project.jjb.member.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.service.MemberService;

@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;

	public MemberController(MemberService memberService) {
		this.memberService = memberService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	MemberSnapshot createMember(@Valid @RequestBody CreateMemberRequest request) {
		return memberService.createMember(request.socialProvider(), request.socialSubject(), request.displayName());
	}

	@GetMapping("/{memberId}")
	MemberSnapshot getMember(@PathVariable UUID memberId) {
		return memberService.getMember(memberId);
	}

	@PostMapping("/{memberId}/phone-verification")
	MemberSnapshot completePhoneVerification(
		@PathVariable UUID memberId,
		@Valid @RequestBody PhoneVerificationRequest request
	) {
		return memberService.completePhoneVerification(memberId, request.phoneNumber(), request.verificationCode());
	}

	@PutMapping("/{memberId}/role")
	MemberSnapshot switchRole(@PathVariable UUID memberId, @Valid @RequestBody SwitchRoleRequest request) {
		return memberService.switchRole(memberId, request.role());
	}

	@PutMapping("/{memberId}/job-seeker-profile")
	MemberSnapshot updateJobSeekerProfile(
		@PathVariable UUID memberId,
		@Valid @RequestBody JobSeekerProfileRequest request
	) {
		return memberService.updateJobSeekerProfile(
			memberId,
			request.availableTime(),
			request.preferredArea(),
			request.desiredHourlyWage(),
			request.experiencedIndustries(),
			request.urgentSubstituteAvailable(),
			request.introduction()
		);
	}

	@PutMapping("/{memberId}/owner-profile")
	MemberSnapshot updateOwnerProfile(
		@PathVariable UUID memberId,
		@Valid @RequestBody OwnerProfileRequest request
	) {
		return memberService.updateOwnerProfile(
			memberId,
			request.storeName(),
			request.storeAddress(),
			request.businessCategory(),
			request.storeIntroduction()
		);
	}

	@PostMapping("/{memberId}/business-verification")
	MemberSnapshot verifyBusiness(
		@PathVariable UUID memberId,
		@Valid @RequestBody BusinessVerificationRequest request
	) {
		return memberService.verifyBusiness(
			memberId,
			request.businessRegistrationNumber(),
			request.representativeName(),
			request.openingDate()
		);
	}

	record CreateMemberRequest(
		@NotBlank String socialProvider,
		@NotBlank String socialSubject,
		@NotBlank String displayName
	) {
	}

	record PhoneVerificationRequest(
		@NotBlank String phoneNumber,
		@NotBlank String verificationCode
	) {
	}

	record SwitchRoleRequest(
		@NotNull MemberRole role
	) {
	}

	record JobSeekerProfileRequest(
		@NotBlank String availableTime,
		@NotBlank String preferredArea,
		@Positive int desiredHourlyWage,
		List<String> experiencedIndustries,
		boolean urgentSubstituteAvailable,
		@NotBlank String introduction
	) {
	}

	record OwnerProfileRequest(
		@NotBlank String storeName,
		@NotBlank String storeAddress,
		@NotBlank String businessCategory,
		String storeIntroduction
	) {
	}

	record BusinessVerificationRequest(
		@NotBlank String businessRegistrationNumber,
		@NotBlank String representativeName,
		@NotNull LocalDate openingDate
	) {
	}
}
