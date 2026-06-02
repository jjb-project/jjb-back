package project.jjb.web.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
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
import project.jjb.matching.domain.MatchingRecommendation;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.Review;
import project.jjb.matching.service.MatchingRecommendationService;
import project.jjb.matching.service.MatchingService;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.service.MemberService;
import project.jjb.web.LiveUpdateService;
import project.jjb.web.WebSessionKeys;

@Controller
public class JjbPageController {

	private static final Pattern PROFILE_TIME_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");

	private final MemberService memberService;
	private final MatchingService matchingService;
	private final MatchingRecommendationService matchingRecommendationService;
	private final LiveUpdateService liveUpdateService;
	private final int minimumHourlyWage;

	public JjbPageController(
		MemberService memberService,
		MatchingService matchingService,
		MatchingRecommendationService matchingRecommendationService,
		LiveUpdateService liveUpdateService,
		@Value("${jjb.minimum-hourly-wage:10320}") int minimumHourlyWage
	) {
		this.memberService = memberService;
		this.matchingService = matchingService;
		this.matchingRecommendationService = matchingRecommendationService;
		this.liveUpdateService = liveUpdateService;
		this.minimumHourlyWage = minimumHourlyWage;
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
		return "redirect:/role";
	}

	@PostMapping("/login/local")
	String loginLocal(
		@RequestParam String email,
		@RequestParam String password,
		HttpSession session
	) {
		MemberSnapshot member = memberService.loginLocalMember(email, password);
		setSessionMember(session, member);
		return "redirect:" + nextPath(member);
	}

	@GetMapping("/signup")
	String signup(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "signup";
	}

	@PostMapping("/signup/local")
	String signupLocal(
		@RequestParam String displayName,
		@RequestParam String email,
		@RequestParam String password,
		HttpSession session
	) {
		MemberSnapshot member = memberService.registerLocalMember(email, password, displayName);
		setSessionMember(session, member);
		return "redirect:" + nextPath(member);
	}

	@GetMapping("/role")
	String role(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		return "role";
	}

	@PostMapping("/role")
	String chooseRole(@RequestParam MemberRole role, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		MemberSnapshot updated = memberService.switchRole(member.id(), role);
		setSessionMember(session, updated);
		return role == MemberRole.OWNER ? "redirect:/boss/home" : "redirect:/worker/home";
	}

	@GetMapping("/worker/home")
	String workerHome(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		List<MatchRequestSnapshot> requests = matchingService.listMatchRequestsForJobSeeker(member.id());
		model.addAttribute("requestCards", requestCards(requests.stream()
			.filter(request -> request.requestedBy().name().equals("OWNER"))
			.toList()));
		model.addAttribute("sentRequestCards", requestCards(requests.stream()
			.filter(request -> request.requestedBy().name().equals("JOB_SEEKER"))
			.toList()));
		model.addAttribute("storeCards", storeCards(member.id()));
		model.addAttribute("recommendationUrl", "/api/members/" + member.id() + "/store-recommendations");
		return "worker_home";
	}

	@GetMapping({"/worker/profile/new", "/worker/profile/edit"})
	String workerProfileForm(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("profile", member.jobSeekerProfile());
		model.addAttribute("minimumHourlyWage", minimumHourlyWage);
		model.addAttribute("defaultAvailableTime", "오늘 18:00-23:00");
		String availableTime = member.jobSeekerProfile() == null ? "오늘 18:00-23:00" : member.jobSeekerProfile().availableTime();
		model.addAttribute("availableStartTime", profileStartTime(availableTime));
		model.addAttribute("availableEndTime", profileEndTime(availableTime));
		model.addAttribute(
			"experiencedIndustries",
			member.jobSeekerProfile() == null ? "" : String.join(", ", member.jobSeekerProfile().experiencedIndustries())
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
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MemberSnapshot updated = memberService.updateJobSeekerProfile(
			member.id(),
			availableTime,
			preferredArea,
			desiredHourlyWage,
			splitList(experiencedIndustries),
			urgentSubstituteAvailable,
			introduction
		);
		setSessionMember(session, updated);
		liveUpdateService.publish("profiles");
		return "redirect:/worker/home";
	}

	@GetMapping("/worker/requests/{id}")
	String workerRequestDetail(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("request", requestCard(id));
		return "request_detail";
	}

	@PostMapping("/worker/requests/{id}/accept")
	String acceptRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.acceptMatchRequest(id, member.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + id;
	}

	@PostMapping("/worker/requests/{id}/decline")
	String declineRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.declineMatchRequest(id, member.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/worker/home";
	}

	@PostMapping("/worker/requests/{id}/cancel")
	String cancelWorkerSentRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.cancelMatchRequestByJobSeeker(id, member.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/worker/home";
	}

	@PostMapping("/worker/match-requests")
	String createWorkerMatchRequest(
		@RequestParam UUID ownerId,
		@RequestParam(defaultValue = "근무 지원합니다.") String message,
		HttpSession session
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MatchRequestSnapshot request = matchingService.createMatchRequestFromJobSeeker(member.id(), ownerId, message);
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + request.id();
	}

	@GetMapping("/boss/home")
	String bossHome(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("recruitments", recruitmentCards(matchingService.listRecruitmentsForOwner(member.id())));
		model.addAttribute("candidates", candidates(member.id()));
		model.addAttribute("workerRequests", ownerRequestCards(member.id()));
		model.addAttribute("memberVerified", member.verification().businessVerified());
		model.addAttribute("recommendationUrl", "/api/members/" + member.id() + "/candidate-recommendations");
		return "boss_home";
	}

	@GetMapping("/boss/verify")
	String bossVerify(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
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
		MemberSnapshot owner = ensureRole(session, MemberRole.OWNER);
		memberService.updateOwnerProfile(owner.id(), storeName, storeAddress, businessCategory, storeIntroduction);
		MemberSnapshot verified = memberService.verifyBusiness(
			owner.id(),
			businessRegistrationNumber,
			representativeName,
			openingDate
		);
		setSessionMember(session, verified);
		liveUpdateService.publish("stores");
		return "redirect:/boss/home";
	}

	@GetMapping("/boss/recruitments/new")
	String recruitmentForm(Model model, HttpSession session) {
		MemberSnapshot member = requireBusinessVerifiedOwner(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		return "boss_post";
	}

	@PostMapping("/boss/recruitments")
	String createRecruitment(
		@RequestParam String title,
		@RequestParam LocalDate workDate,
		@RequestParam LocalTime startTime,
		@RequestParam LocalTime endTime,
		@RequestParam String workplaceAddress,
		@RequestParam int hourlyWage,
		HttpSession session
	) {
		MemberSnapshot member = requireBusinessVerifiedOwner(session);
		Recruitment recruitment = matchingService.createRecruitment(
			member.id(),
			title,
			workDate,
			startTime,
			endTime,
			workplaceAddress,
			hourlyWage
		);
		liveUpdateService.publish("recruitments");
		return "redirect:/boss/recruitments/" + recruitment.id();
	}

	@GetMapping("/boss/recruitments/{id}")
	String recruitmentDetail(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		Recruitment recruitment = matchingService.getRecruitment(id);
		if (!recruitment.ownerId().equals(member.id())) {
			throw ApiException.forbidden("RECRUITMENT_NOT_OWNED", "Only the recruitment owner can view recommendations.");
		}
		List<MatchingRecommendation> recommendations = matchingRecommendationService.recommendForRecruitment(id);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("recruitment", recruitmentCard(recruitment));
		model.addAttribute("recommendations", recommendations);
		model.addAttribute("memberVerified", member.verification().businessVerified());
		return "boss_recruitment_detail";
	}

	@GetMapping("/boss/candidates/{id}")
	String candidateDetail(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("candidate", candidate(id));
		model.addAttribute("recruitments", matchingService.listRecruitmentsForOwner(member.id()));
		model.addAttribute("memberVerified", member.verification().businessVerified());
		return "candidate_detail";
	}

	@PostMapping("/boss/match-requests")
	String createMatchRequest(
		@RequestParam UUID jobSeekerId,
		@RequestParam(required = false) UUID recruitmentId,
		@RequestParam(defaultValue = "오늘 근무 가능하실까요?") String message,
		HttpSession session
	) {
		MemberSnapshot owner = requireBusinessVerifiedOwner(session);
		MatchRequestSnapshot request = matchingService.createMatchRequest(owner.id(), jobSeekerId, recruitmentId, message);
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + request.id();
	}

	@PostMapping("/boss/requests/{id}/accept")
	String acceptWorkerRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot owner = requireBusinessVerifiedOwner(session);
		matchingService.acceptMatchRequestByOwner(id, owner.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + id;
	}

	@PostMapping("/boss/requests/{id}/decline")
	String declineWorkerRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot owner = requireBusinessVerifiedOwner(session);
		matchingService.declineMatchRequestByOwner(id, owner.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/boss/home";
	}

	@GetMapping("/match-confirmed")
	String matchConfirmed(
		@RequestParam(required = false) UUID matchRequestId,
		Model model,
		HttpSession session
	) {
		MemberSnapshot member = addSessionMember(model, session);
		if (matchRequestId != null) {
			model.addAttribute("matchRequest", requestCard(matchRequestId));
		}
		model.addAttribute("member", member);
		return "match_confirmed";
	}

	@GetMapping("/mypage")
	String myPage(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("reviewSummary", reviewSummary(member.id()));
		return "mypage";
	}

	@GetMapping("/eval")
	String eval(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("evaluationOptions", evaluationOptions(member));
		return "eval";
	}

	@PostMapping("/eval")
	String saveEvaluation(
		@RequestParam UUID matchRequestId,
		@RequestParam UUID targetId,
		@RequestParam int rating,
		@RequestParam String review,
		HttpSession session
	) {
		MemberSnapshot member = requireMember(session);
		matchingService.createReview(matchRequestId, member.id(), targetId, rating, review);
		liveUpdateService.publish("reviews");
		return "redirect:/mypage";
	}

	@GetMapping("/demo")
	String demo() {
		return "redirect:/mypage";
	}

	@ExceptionHandler(ApiException.class)
	String handlePageException(
		ApiException exception,
		HttpServletRequest request,
		RedirectAttributes redirectAttributes
	) {
		redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
		if ("BUSINESS_VERIFICATION_REQUIRED".equals(exception.code())) {
			return "redirect:/boss/verify";
		}
		String requestUri = request.getRequestURI();
		if (requestUri != null && requestUri.startsWith("/signup")) {
			return "redirect:/signup";
		}
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

	private MemberSnapshot requireMember(HttpSession session) {
		return currentMember(session)
			.orElseThrow(() -> ApiException.forbidden("LOGIN_REQUIRED", "로그인이 필요합니다."));
	}

	private MemberSnapshot ensureRole(HttpSession session, MemberRole role) {
		MemberSnapshot member = requireMember(session);
		if (member.activeRole() == role) {
			return member;
		}
		MemberSnapshot updated = memberService.switchRole(member.id(), role);
		setSessionMember(session, updated);
		return updated;
	}

	private MemberSnapshot requireBusinessVerifiedOwner(HttpSession session) {
		MemberSnapshot owner = ensureRole(session, MemberRole.OWNER);
		if (!owner.verification().businessVerified()) {
			throw ApiException.forbidden("BUSINESS_VERIFICATION_REQUIRED", "사업자 인증이 필요합니다.");
		}
		return owner;
	}

	private void setSessionMember(HttpSession session, MemberSnapshot member) {
		session.setAttribute(WebSessionKeys.CURRENT_MEMBER_ID, member.id());
		session.setAttribute(WebSessionKeys.ACTIVE_ROLE, member.activeRole());
	}

	private String nextPath(MemberSnapshot member) {
		if (member.activeRole() == null) {
			return "/role";
		}
		return member.activeRole() == MemberRole.OWNER ? "/boss/home" : "/worker/home";
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

	private String profileStartTime(String availableTime) {
		Matcher matcher = PROFILE_TIME_RANGE.matcher(availableTime == null ? "" : availableTime);
		return matcher.find() ? matcher.group(1) : "18:00";
	}

	private String profileEndTime(String availableTime) {
		Matcher matcher = PROFILE_TIME_RANGE.matcher(availableTime == null ? "" : availableTime);
		return matcher.find() ? matcher.group(2) : "23:00";
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
			statusLabel(request.status().name())
		);
	}

	private List<RecruitmentCard> recruitmentCards(List<Recruitment> recruitments) {
		return recruitments.stream()
			.map(this::recruitmentCard)
			.toList();
	}

	private RecruitmentCard recruitmentCard(Recruitment recruitment) {
		return new RecruitmentCard(
			recruitment.id(),
			recruitment.title(),
			recruitment.workTime(),
			recruitment.workplaceAddress(),
			recruitment.hourlyWage(),
			statusLabel(recruitment.status().name())
		);
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

	private List<StoreCard> storeCards(UUID excludedMemberId) {
		return memberService.listVerifiedOwnersWithProfiles().stream()
			.filter(member -> excludedMemberId == null || !member.id().equals(excludedMemberId))
			.map(this::storeCard)
			.toList();
	}

	private StoreCard storeCard(MemberSnapshot owner) {
		OwnerProfile profile = owner.ownerProfile();
		return new StoreCard(
			owner.id(),
			storeName(owner),
			profile == null ? "" : profile.storeAddress(),
			profile == null ? "" : profile.businessCategory(),
			profile == null ? "" : profile.storeIntroduction()
		);
	}

	private List<OwnerRequestCard> ownerRequestCards(UUID ownerId) {
		return matchingService.listMatchRequestsForOwner(ownerId).stream()
			.filter(request -> request.requestedBy().name().equals("JOB_SEEKER"))
			.map(request -> {
				MemberSnapshot jobSeeker = memberService.getMember(request.jobSeekerId());
				return new OwnerRequestCard(
					request.id(),
					jobSeeker.displayName(),
					request.message(),
					statusLabel(request.status().name())
				);
			})
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

	private List<EvaluationOption> evaluationOptions(MemberSnapshot member) {
		return matchingService.listAcceptedMatchRequestsForParticipant(member.id()).stream()
			.map(request -> {
				boolean memberIsOwner = request.ownerId().equals(member.id());
				UUID targetId = memberIsOwner ? request.jobSeekerId() : request.ownerId();
				MemberSnapshot target = memberService.getMember(targetId);
				String targetName = memberIsOwner ? target.displayName() : storeName(target);
				String context = request.recruitmentId() == null
					? request.message()
					: matchingService.getRecruitment(request.recruitmentId()).title();
				return new EvaluationOption(request.id(), targetId, targetName, context);
			})
			.toList();
	}

	private ReviewSummary reviewSummary(UUID memberId) {
		List<Review> reviews = matchingService.listReviewsForTarget(memberId);
		double average = reviews.stream()
			.mapToInt(Review::rating)
			.average()
			.orElse(0.0);
		return new ReviewSummary(Math.round(average * 10.0) / 10.0, reviews.size());
	}

	private String statusLabel(String status) {
		return switch (status) {
			case "OPEN" -> "모집 중";
			case "CLOSED" -> "마감";
			case "REQUESTED" -> "요청 중";
			case "ACCEPTED" -> "수락됨";
			case "DECLINED" -> "거절됨";
			case "CANCELED" -> "취소됨";
			default -> status;
		};
	}

	record RequestCard(UUID id, String storeName, String workTime, String message, String hourlyWage, String status) {
	}

	record RecruitmentCard(UUID id, String title, String workTime, String workplaceAddress, int hourlyWage, String status) {
	}

	record CandidateCard(UUID id, String name, String availableTime, String area, String skills, int desiredHourlyWage) {
	}

	record StoreCard(UUID id, String storeName, String storeAddress, String businessCategory, String storeIntroduction) {
	}

	record OwnerRequestCard(UUID id, String jobSeekerName, String message, String status) {
	}

	record EvaluationOption(UUID matchRequestId, UUID targetId, String targetName, String context) {
	}

	record ReviewSummary(double averageRating, int reviewCount) {
	}
}
