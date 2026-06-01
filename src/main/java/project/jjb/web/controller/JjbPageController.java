package project.jjb.web.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import project.jjb.common.ApiException;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.service.MatchingService;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.service.MemberService;
import project.jjb.web.WebSessionKeys;

@Controller
public class JjbPageController {

	private final MemberService memberService;
	private final MatchingService matchingService;

	public JjbPageController(MemberService memberService, MatchingService matchingService) {
		this.memberService = memberService;
		this.matchingService = matchingService;
	}

	@GetMapping("/")
	String start(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "index";
	}

	@PostMapping("/start")
	String startSession(
		@RequestParam(defaultValue = "local") String socialProvider,
		@RequestParam(defaultValue = "바로알바 회원") String displayName,
		HttpSession session
	) {
		String provider = socialProvider.isBlank() ? "local" : socialProvider;
		String subject = "mvc-" + provider.toLowerCase() + "-" + session.getId();
		MemberSnapshot member = memberService.createMember(provider, subject, displayName);
		setSessionMember(session, member);
		return "redirect:/phone";
	}

	@GetMapping("/phone")
	String phone(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "phone";
	}

	@PostMapping("/phone")
	String verifyPhone(
		@RequestParam String phoneNumber,
		@RequestParam(defaultValue = "123456") String verificationCode,
		HttpSession session
	) {
		MemberSnapshot member = ensureMember(session);
		MemberSnapshot verified = memberService.completePhoneVerification(member.id(), phoneNumber, verificationCode);
		setSessionMember(session, verified);
		return "redirect:/role";
	}

	@GetMapping("/role")
	String role(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "role";
	}

	@PostMapping("/role")
	String chooseRole(@RequestParam MemberRole role, HttpSession session) {
		MemberSnapshot member = ensureMember(session);
		if (!member.verification().phoneVerified()) {
			return "redirect:/phone";
		}
		MemberSnapshot updated = memberService.switchRole(member.id(), role);
		setSessionMember(session, updated);
		return role == MemberRole.OWNER ? "redirect:/boss/home" : "redirect:/worker/home";
	}

	@GetMapping("/worker/home")
	String workerHome(Model model, HttpSession session) {
		MemberSnapshot member = addSessionMember(model, session);
		List<RequestCard> requestCards = member == null
			? List.of()
			: requestCards(matchingService.listMatchRequestsForJobSeeker(member.id()));
		model.addAttribute("requestCards", requestCards);
		return "worker_home";
	}

	@GetMapping({"/worker/profile/new", "/worker/profile/edit"})
	String workerProfileForm(Model model, HttpSession session) {
		MemberSnapshot member = addSessionMember(model, session);
		model.addAttribute("profile", member == null ? null : member.jobSeekerProfile());
		model.addAttribute(
			"experiencedIndustries",
			member == null || member.jobSeekerProfile() == null
				? ""
				: String.join(", ", member.jobSeekerProfile().experiencedIndustries())
		);
		return "worker_profile";
	}

	@PostMapping("/worker/profile")
	String saveWorkerProfile(
		@RequestParam String availableTime,
		@RequestParam String preferredArea,
		@RequestParam int desiredHourlyWage,
		@RequestParam(defaultValue = "") String experiencedIndustries,
		@RequestParam(defaultValue = "false") boolean urgentSubstituteAvailable,
		@RequestParam String introduction,
		HttpSession session
	) {
		MemberSnapshot member = ensureMember(session);
		if (!member.verification().phoneVerified()) {
			return "redirect:/phone";
		}
		MemberSnapshot roleReady = member.activeRole() == MemberRole.JOB_SEEKER
			? member
			: memberService.switchRole(member.id(), MemberRole.JOB_SEEKER);
		MemberSnapshot updated = memberService.updateJobSeekerProfile(
			roleReady.id(),
			availableTime,
			preferredArea,
			desiredHourlyWage,
			splitList(experiencedIndustries),
			urgentSubstituteAvailable,
			introduction
		);
		setSessionMember(session, updated);
		return "redirect:/worker/home";
	}

	@GetMapping("/worker/requests/{id}")
	String workerRequestDetail(@PathVariable UUID id, Model model, HttpSession session) {
		addSessionMember(model, session);
		model.addAttribute("request", requestCard(id));
		return "request_detail";
	}

	@PostMapping("/worker/requests/{id}/accept")
	String acceptRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = currentMember(session).orElse(null);
		if (member != null) {
			try {
				matchingService.acceptMatchRequest(id, member.id());
			}
			catch (ApiException ignored) {
				return "redirect:/match-confirmed";
			}
		}
		return "redirect:/match-confirmed";
	}

	@GetMapping("/boss/home")
	String bossHome(Model model, HttpSession session) {
		MemberSnapshot member = addSessionMember(model, session);
		model.addAttribute("recruitments", recruitmentCards(matchingService.listRecruitments()));
		model.addAttribute("candidates", candidates(member == null ? null : member.id()));
		model.addAttribute("memberVerified", member != null && member.verification().businessVerified());
		return "boss_home";
	}

	@GetMapping("/boss/verify")
	String bossVerify(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "boss_verify";
	}

	@PostMapping("/boss/verify")
	String verifyBoss(
		@RequestParam String businessRegistrationNumber,
		@RequestParam String representativeName,
		@RequestParam LocalDate openingDate,
		@RequestParam String storeName,
		@RequestParam String storeAddress,
		@RequestParam String businessCategory,
		@RequestParam(defaultValue = "") String storeIntroduction,
		HttpSession session
	) {
		MemberSnapshot member = ensureMember(session);
		if (!member.verification().phoneVerified()) {
			return "redirect:/phone";
		}
		MemberSnapshot owner = member.activeRole() == MemberRole.OWNER
			? member
			: memberService.switchRole(member.id(), MemberRole.OWNER);
		memberService.updateOwnerProfile(owner.id(), storeName, storeAddress, businessCategory, storeIntroduction);
		MemberSnapshot verified = memberService.verifyBusiness(
			owner.id(),
			businessRegistrationNumber,
			representativeName,
			openingDate
		);
		setSessionMember(session, verified);
		return "redirect:/boss/home";
	}

	@GetMapping("/boss/recruitments/new")
	String recruitmentForm(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "boss_post";
	}

	@PostMapping("/boss/recruitments")
	String createRecruitment(
		@RequestParam String title,
		@RequestParam String workDate,
		@RequestParam String startTime,
		@RequestParam String endTime,
		@RequestParam String workplaceAddress,
		@RequestParam int hourlyWage,
		HttpSession session
	) {
		MemberSnapshot member = ensureMember(session);
		if (!member.verification().businessVerified()) {
			return "redirect:/boss/verify";
		}
		String workTime = workDate + " " + startTime + "-" + endTime;
		matchingService.createRecruitment(member.id(), title, workTime, workplaceAddress, hourlyWage);
		return "redirect:/boss/home";
	}

	@GetMapping("/boss/candidates/{id}")
	String candidateDetail(@PathVariable UUID id, Model model, HttpSession session) {
		addSessionMember(model, session);
		model.addAttribute("candidate", candidate(id));
		model.addAttribute("recruitments", matchingService.listRecruitments());
		return "candidate_detail";
	}

	@PostMapping("/boss/match-requests")
	String createMatchRequest(
		@RequestParam UUID jobSeekerId,
		@RequestParam(required = false) UUID recruitmentId,
		@RequestParam(defaultValue = "오늘 근무 가능하실까요?") String message,
		HttpSession session
	) {
		MemberSnapshot owner = ensureMember(session);
		if (!owner.verification().businessVerified()) {
			return "redirect:/boss/verify";
		}
		matchingService.createMatchRequest(owner.id(), jobSeekerId, recruitmentId, message);
		return "redirect:/match-confirmed";
	}

	@GetMapping("/match-confirmed")
	String matchConfirmed(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "match_confirmed";
	}

	@GetMapping("/mypage")
	String myPage(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "mypage";
	}

	@GetMapping("/eval")
	String eval(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "eval";
	}

	@GetMapping("/demo")
	String demo() {
		return "redirect:/demo/index.html";
	}

	@ExceptionHandler(ApiException.class)
	String handlePageException(ApiException exception, RedirectAttributes redirectAttributes) {
		redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		return "redirect:/";
	}

	private MemberSnapshot addSessionMember(Model model, HttpSession session) {
		MemberSnapshot member = currentMember(session).orElse(null);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member == null ? session.getAttribute(WebSessionKeys.ACTIVE_ROLE) : member.activeRole());
		return member;
	}

	private Optional<MemberSnapshot> currentMember(HttpSession session) {
		Object memberId = session.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);
		if (!(memberId instanceof UUID id)) {
			return Optional.empty();
		}
		try {
			return Optional.of(memberService.getMember(id));
		}
		catch (ApiException ignored) {
			session.removeAttribute(WebSessionKeys.CURRENT_MEMBER_ID);
			session.removeAttribute(WebSessionKeys.ACTIVE_ROLE);
			return Optional.empty();
		}
	}

	private MemberSnapshot ensureMember(HttpSession session) {
		return currentMember(session).orElseGet(() -> {
			MemberSnapshot member = memberService.createMember("local", "mvc-local-" + session.getId(), "바로알바 회원");
			setSessionMember(session, member);
			return member;
		});
	}

	private void setSessionMember(HttpSession session, MemberSnapshot member) {
		session.setAttribute(WebSessionKeys.CURRENT_MEMBER_ID, member.id());
		session.setAttribute(WebSessionKeys.ACTIVE_ROLE, member.activeRole());
	}

	private List<String> splitList(String csv) {
		if (csv == null || csv.isBlank()) {
			return List.of();
		}
		return Arrays.stream(csv.split(","))
			.map(String::trim)
			.filter(value -> !value.isBlank())
			.toList();
	}

	private List<RequestCard> requestCards(List<MatchRequestSnapshot> requests) {
		return requests.stream()
			.map(this::requestCard)
			.toList();
	}

	private RequestCard requestCard(UUID id) {
		return requestCard(matchingService.getMatchRequest(id));
	}

	private RequestCard requestCard(MatchRequestSnapshot request) {
		MemberSnapshot owner = memberService.getMember(request.ownerId());
		Recruitment recruitment = request.recruitmentId() == null
			? null
			: matchingService.getRecruitment(request.recruitmentId());
		return new RequestCard(
			request.id(),
			storeName(owner),
			recruitment == null ? "모집 미지정" : recruitment.workTime(),
			request.message(),
			recruitment == null ? "협의" : recruitment.hourlyWage() + "원",
			request.status().name()
		);
	}

	private List<RecruitmentCard> recruitmentCards(List<Recruitment> recruitments) {
		return recruitments.stream()
			.map(recruitment -> new RecruitmentCard(
				recruitment.id(),
				recruitment.title(),
				recruitment.workTime(),
				recruitment.workplaceAddress(),
				recruitment.hourlyWage()
			))
			.toList();
	}

	private String storeName(MemberSnapshot owner) {
		OwnerProfile ownerProfile = owner.ownerProfile();
		if (ownerProfile != null && ownerProfile.storeName() != null && !ownerProfile.storeName().isBlank()) {
			return ownerProfile.storeName();
		}
		return owner.displayName();
	}

	private List<CandidateCard> candidates(UUID excludedMemberId) {
		return memberService.listJobSeekersWithProfiles().stream()
			.filter(member -> excludedMemberId == null || !member.id().equals(excludedMemberId))
			.map(this::candidateCard)
			.toList();
	}

	private CandidateCard candidate(UUID id) {
		return candidateCard(memberService.getMember(id));
	}

	private CandidateCard candidateCard(MemberSnapshot member) {
		JobSeekerProfile profile = member.jobSeekerProfile();
		if (profile == null) {
			throw ApiException.notFound("JOB_SEEKER_NOT_FOUND", "Job seeker profile was not found.");
		}
		return new CandidateCard(
			member.id(),
			member.displayName(),
			profile.availableTime(),
			profile.preferredArea(),
			profile.experiencedIndustries().isEmpty() ? "경력 미입력" : String.join(", ", profile.experiencedIndustries()),
			profile.desiredHourlyWage()
		);
	}

	record RequestCard(UUID id, String storeName, String workTime, String message, String hourlyWage, String status) {
	}

	record RecruitmentCard(UUID id, String title, String workTime, String workplaceAddress, int hourlyWage) {
	}

	record CandidateCard(UUID id, String name, String availableTime, String area, String skills, int desiredHourlyWage) {
	}
}
