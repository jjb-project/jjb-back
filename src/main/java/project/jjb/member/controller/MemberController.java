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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.service.MemberService;
import project.jjb.web.LiveUpdateService;

@RestController
@RequestMapping("/api/members")
public class MemberController {

	private final MemberService memberService;
	private final LiveUpdateService liveUpdateService;

	public MemberController(MemberService memberService, LiveUpdateService liveUpdateService) {
		this.memberService = memberService;
		this.liveUpdateService = liveUpdateService;
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

	@GetMapping("/email-availability")
	EmailAvailabilityResponse checkEmailAvailability(@RequestParam String email) {
		MemberService.LocalEmailAvailability availability = memberService.checkLocalEmailAvailability(email);
		return new EmailAvailabilityResponse(
			availability.available(),
			availability.normalizedEmail(),
			availability.message()
		);
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
		MemberSnapshot member = memberService.updateJobSeekerProfile(
			memberId,
			request.availableTime(),
			request.preferredArea(),
			request.desiredHourlyWage(),
			request.experiencedIndustries(),
			request.urgentSubstituteAvailable(),
			request.introduction()
		);
		liveUpdateService.publish("profiles");
		return member;
	}

	@PutMapping("/{memberId}/owner-profile")
	MemberSnapshot updateOwnerProfile(
		@PathVariable UUID memberId,
		@Valid @RequestBody OwnerProfileRequest request
	) {
		MemberSnapshot member = memberService.updateOwnerProfile(
			memberId,
			request.storeName(),
			request.storeAddress(),
			request.businessCategory(),
			request.storeIntroduction()
		);
		liveUpdateService.publish("stores");
		return member;
	}

	@PostMapping("/{memberId}/business-verification")
	MemberSnapshot verifyBusiness(
		@PathVariable UUID memberId,
		@Valid @RequestBody BusinessVerificationRequest request
	) {
		MemberSnapshot member = memberService.verifyBusiness(
			memberId,
			request.businessRegistrationNumber(),
			request.representativeName(),
			request.openingDate()
		);
		liveUpdateService.publish("stores");
		return member;
	}

	record CreateMemberRequest(
		@NotBlank String socialProvider,
		@NotBlank String socialSubject,
		@NotBlank String displayName
	) {
	}

	record EmailAvailabilityResponse(
		boolean available,
		String normalizedEmail,
		String message
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
