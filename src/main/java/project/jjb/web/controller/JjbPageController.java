package project.jjb.web.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
import project.jjb.matching.domain.FavoriteTargetType;
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
		model.addAttribute("recentJobs", jobListingCards(openRecruitments().stream().limit(5).toList()));
		return "index";
	}

	@GetMapping("/jobs")
	String jobs(Model model, HttpSession session) {
		addSessionMember(model, session);
		model.addAttribute("jobCards", jobListingCards(openRecruitments()));
		return "jobs";
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
		@RequestParam(required = false) String username,
		@RequestParam(required = false) String email,
		@RequestParam String password,
		HttpSession session
	) {
		MemberSnapshot member = memberService.loginLocalMember(localIdentifier(username, email), password);
		setSessionMember(session, member);
		return "redirect:" + nextPath(member);
	}

	@GetMapping("/login/local")
	String loginLocalFallback() {
		return "redirect:/";
	}

	@GetMapping("/signup")
	String signup(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "signup";
	}

	@PostMapping("/signup/local")
	String signupLocal(
		@RequestParam String displayName,
		@RequestParam(required = false) String username,
		@RequestParam(required = false) String email,
		@RequestParam String password,
		HttpSession session
	) {
		MemberSnapshot member = memberService.registerLocalMember(localIdentifier(username, email), password, displayName);
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
	String workerHome(
		@RequestParam(defaultValue = "") String keyword,
		@RequestParam(defaultValue = "") String region,
		@RequestParam(defaultValue = "") String category,
		@RequestParam(defaultValue = "false") boolean favoritesOnly,
		Model model,
		HttpSession session
	) {
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
		model.addAttribute("storeCards", filterStoreCards(storeCards(member.id()), keyword, region, category, favoritesOnly));
		model.addAttribute("keyword", keyword);
		model.addAttribute("region", region);
		model.addAttribute("category", category);
		model.addAttribute("favoritesOnly", favoritesOnly);
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

	@GetMapping("/worker/stores/{id}")
	String workerStoreDetail(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MemberSnapshot owner = memberService.getMember(id);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("store", storeCard(owner));
		model.addAttribute("reviewSummary", reviewSummary(id));
		model.addAttribute("reviews", reviewCards(matchingService.listReviewsForTarget(id)));
		model.addAttribute("favorite", matchingService.isFavorite(member.id(), id, FavoriteTargetType.OWNER));
		return "store_detail";
	}

	@PostMapping("/worker/stores/{id}/favorite")
	String toggleStoreFavorite(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.toggleFavorite(member.id(), id, FavoriteTargetType.OWNER);
		return "redirect:/worker/home";
	}

	@GetMapping("/boss/home")
	String bossHome(
		@RequestParam(defaultValue = "") String keyword,
		@RequestParam(defaultValue = "") String region,
		@RequestParam(defaultValue = "") String industry,
		@RequestParam(defaultValue = "false") boolean favoritesOnly,
		Model model,
		HttpSession session
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("recruitments", recruitmentCards(matchingService.listRecruitmentsForOwner(member.id())));
		model.addAttribute("candidates", filterCandidateCards(member.id(), candidates(member.id()), keyword, region, industry, favoritesOnly));
		model.addAttribute("workerRequests", ownerRequestCards(member.id()));
		model.addAttribute("memberVerified", member.verification().businessVerified());
		model.addAttribute("keyword", keyword);
		model.addAttribute("region", region);
		model.addAttribute("industry", industry);
		model.addAttribute("favoritesOnly", favoritesOnly);
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
		model.addAttribute("reviewSummary", reviewSummary(id));
		model.addAttribute("reviews", reviewCards(matchingService.listReviewsForTarget(id)));
		model.addAttribute("favorite", matchingService.isFavorite(member.id(), id, FavoriteTargetType.JOB_SEEKER));
		return "candidate_detail";
	}

	@PostMapping("/boss/candidates/{id}/favorite")
	String toggleCandidateFavorite(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		matchingService.toggleFavorite(member.id(), id, FavoriteTargetType.JOB_SEEKER);
		return "redirect:/boss/home";
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
		model.addAttribute("receivedReviews", reviewCards(matchingService.listReviewsForTarget(member.id())));
		model.addAttribute("writtenReviews", reviewCards(matchingService.listReviewsByEvaluator(member.id())));
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

	private String localIdentifier(String username, String email) {
		if (username != null && !username.isBlank()) {
			return username;
		}
		return email;
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

	private List<Recruitment> openRecruitments() {
		return matchingService.listRecruitments().stream()
			.filter(Recruitment::isOpen)
			.toList();
	}

	private List<JobListingCard> jobListingCards(List<Recruitment> recruitments) {
		return recruitments.stream()
			.map(this::jobListingCard)
			.toList();
	}

	private JobListingCard jobListingCard(Recruitment recruitment) {
		MemberSnapshot owner = memberService.getMember(recruitment.ownerId());
		OwnerProfile profile = owner.ownerProfile();
		return new JobListingCard(
			recruitment.id(),
			recruitment.title(),
			storeName(owner),
			profile == null ? "" : profile.businessCategory(),
			recruitment.workplaceAddress(),
			recruitment.workTime(),
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
		Set<UUID> favoriteIds = excludedMemberId == null
			? Set.of()
			: matchingService.listFavoriteTargetIds(excludedMemberId, FavoriteTargetType.OWNER).stream().collect(Collectors.toSet());
		return memberService.listVerifiedOwnersWithProfiles().stream()
			.filter(member -> excludedMemberId == null || !member.id().equals(excludedMemberId))
			.map(member -> storeCard(member, favoriteIds.contains(member.id())))
			.toList();
	}

	private StoreCard storeCard(MemberSnapshot owner) {
		return storeCard(owner, false);
	}

	private StoreCard storeCard(MemberSnapshot owner, boolean favorite) {
		OwnerProfile profile = owner.ownerProfile();
		ReviewSummary summary = reviewSummary(owner.id());
		return new StoreCard(
			owner.id(),
			storeName(owner),
			profile == null ? "" : profile.storeAddress(),
			profile == null ? "" : profile.businessCategory(),
			profile == null ? "" : profile.storeIntroduction(),
			summary.averageRating(),
			summary.reviewCount(),
			favorite,
			owner.verification().businessVerified() ? "사업자 인증" : "인증 대기"
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
		ReviewSummary summary = reviewSummary(member.id());
		return new CandidateCard(
			member.id(),
			member.displayName(),
			profile.availableTime(),
			profile.preferredArea(),
			profile.experiencedIndustries().isEmpty() ? "경력 미입력" : String.join(", ", profile.experiencedIndustries()),
			profile.desiredHourlyWage(),
			summary.averageRating(),
			summary.reviewCount(),
			false,
			profile.urgentSubstituteAvailable() ? "긴급 대타 가능" : "일반 지원"
		);
	}

	private List<EvaluationOption> evaluationOptions(MemberSnapshot member) {
		return matchingService.listAcceptedMatchRequestsForParticipant(member.id()).stream()
			.filter(request -> !matchingService.hasReviewed(request.id(), member.id()))
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

	private List<ReviewCard> reviewCards(List<Review> reviews) {
		return reviews.stream()
			.map(review -> {
				MemberSnapshot evaluator = memberService.getMember(review.evaluatorId());
				MemberSnapshot target = memberService.getMember(review.targetId());
				String context = matchingService.getMatchRequest(review.matchRequestId()).message();
				return new ReviewCard(
					review.id(),
					review.rating(),
					review.review(),
					evaluator.displayName(),
					target.displayName(),
					context,
					review.createdAt().toString()
				);
			})
			.toList();
	}

	private List<StoreCard> filterStoreCards(
		List<StoreCard> stores,
		String keyword,
		String region,
		String category,
		boolean favoritesOnly
	) {
		return stores.stream()
			.filter(store -> !favoritesOnly || store.favorite())
			.filter(store -> containsIgnoreCase(store.storeName() + " " + store.storeIntroduction(), keyword))
			.filter(store -> containsIgnoreCase(store.storeAddress(), region))
			.filter(store -> containsIgnoreCase(store.businessCategory(), category))
			.toList();
	}

	private List<CandidateCard> filterCandidateCards(
		UUID ownerId,
		List<CandidateCard> candidates,
		String keyword,
		String region,
		String industry,
		boolean favoritesOnly
	) {
		Set<UUID> favoriteIds = matchingService.listFavoriteTargetIds(ownerId, FavoriteTargetType.JOB_SEEKER).stream()
			.collect(Collectors.toSet());
		return candidates.stream()
			.map(candidate -> new CandidateCard(
				candidate.id(),
				candidate.name(),
				candidate.availableTime(),
				candidate.area(),
				candidate.skills(),
				candidate.desiredHourlyWage(),
				candidate.averageRating(),
				candidate.reviewCount(),
				favoriteIds.contains(candidate.id()),
				candidate.trustLabel()
			))
			.filter(candidate -> !favoritesOnly || candidate.favorite())
			.filter(candidate -> containsIgnoreCase(candidate.name() + " " + candidate.skills(), keyword))
			.filter(candidate -> containsIgnoreCase(candidate.area(), region))
			.filter(candidate -> containsIgnoreCase(candidate.skills(), industry))
			.toList();
	}

	private boolean containsIgnoreCase(String source, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return true;
		}
		return source != null && source.toLowerCase().contains(keyword.trim().toLowerCase());
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

	record JobListingCard(UUID id, String title, String storeName, String businessCategory, String workplaceAddress, String workTime, int hourlyWage, String status) {
	}

	record CandidateCard(UUID id, String name, String availableTime, String area, String skills, int desiredHourlyWage, double averageRating, int reviewCount, boolean favorite, String trustLabel) {
	}

	record StoreCard(UUID id, String storeName, String storeAddress, String businessCategory, String storeIntroduction, double averageRating, int reviewCount, boolean favorite, String trustLabel) {
	}

	record OwnerRequestCard(UUID id, String jobSeekerName, String message, String status) {
	}

	record EvaluationOption(UUID matchRequestId, UUID targetId, String targetName, String context) {
	}

	record ReviewSummary(double averageRating, int reviewCount) {
	}

	record ReviewCard(UUID id, int rating, String review, String evaluatorName, String targetName, String context, String createdAt) {
	}
}
