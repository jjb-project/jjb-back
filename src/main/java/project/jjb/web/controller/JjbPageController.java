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
import project.jjb.matching.domain.ChatMessage;
import project.jjb.matching.domain.FavoriteTargetType;
import project.jjb.matching.domain.MatchRequestInitiator;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.MatchRequestStatus;
import project.jjb.matching.domain.MatchingRecommendation;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.Review;
import project.jjb.matching.domain.SubstituteRequest;
import project.jjb.matching.service.MatchingRecommendationService;
import project.jjb.matching.service.MatchingService;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.service.MemberService;
import project.jjb.notification.domain.Notification;
import project.jjb.notification.domain.NotificationType;
import project.jjb.notification.service.NotificationService;
import project.jjb.web.LiveUpdateService;
import project.jjb.web.WebSessionKeys;

@Controller
public class JjbPageController {

	private static final Pattern PROFILE_TIME_RANGE = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");

	private final MemberService memberService;
	private final MatchingService matchingService;
	private final MatchingRecommendationService matchingRecommendationService;
	private final LiveUpdateService liveUpdateService;
	private final NotificationService notificationService;
	private final int minimumHourlyWage;

	public JjbPageController(
		MemberService memberService,
		MatchingService matchingService,
		MatchingRecommendationService matchingRecommendationService,
		LiveUpdateService liveUpdateService,
		NotificationService notificationService,
		@Value("${jjb.minimum-hourly-wage:10320}") int minimumHourlyWage
	) {
		this.memberService = memberService;
		this.matchingService = matchingService;
		this.matchingRecommendationService = matchingRecommendationService;
		this.liveUpdateService = liveUpdateService;
		this.notificationService = notificationService;
		this.minimumHourlyWage = minimumHourlyWage;
	}

	@GetMapping("/")
	String start(Model model, HttpSession session) {
		MemberSnapshot member = addSessionMember(model, session);
		List<Recruitment> open = openRecruitments();
		List<ProfileCard> allProfiles = profileCards();
		model.addAttribute("recentJobs", jobListingCards(open.stream().limit(3).toList()));
		model.addAttribute("recentProfiles", allProfiles.stream().limit(2).toList());
		model.addAttribute("allJobs", jobListingCards(open));
		model.addAttribute("allProfiles", allProfiles);
		model.addAttribute("urgentJobs", nearbyUrgentJobs(open, member));
		model.addAttribute("minimumHourlyWage", minimumHourlyWage);
		return "index";
	}

	private List<JobListingCard> nearbyUrgentJobs(List<Recruitment> open, MemberSnapshot member) {
		String myRegion = member != null && member.jobSeekerProfile() != null
			? regionToken(member.jobSeekerProfile().preferredArea())
			: "";
		return open.stream()
			.filter(r -> isUrgent(r.workDate(), r.startTime()) || r.tags().contains("급구") || r.instantHire())
			.filter(r -> myRegion.isBlank() || containsIgnoreCase(r.workplaceAddress(), myRegion))
			.sorted(java.util.Comparator.comparing(r -> java.time.LocalDateTime.of(r.workDate(), r.startTime())))
			.limit(8)
			.flatMap(r -> {
				try {
					return java.util.stream.Stream.of(jobListingCard(r));
				}
				catch (Exception ignored) {
					return java.util.stream.Stream.empty();
				}
			})
			.toList();
	}

	private String regionToken(String area) {
		if (area == null || area.isBlank()) {
			return "";
		}
		String first = area.trim().split("\\s+")[0];
		return first.length() >= 2 ? first.substring(0, 2) : first;
	}

	@GetMapping("/jobs/{id}")
	String jobDetail(@PathVariable UUID id, Model model, HttpSession session) {
		addSessionMember(model, session);
		try {
			Recruitment recruitment = matchingService.getRecruitment(id);
			MemberSnapshot owner = memberService.getMember(recruitment.ownerId());
			OwnerProfile ownerProfile = owner.ownerProfile();
			model.addAttribute("job", jobListingCard(recruitment));
			model.addAttribute("tags", recruitment.tags());
			model.addAttribute("description", recruitment.description());
			model.addAttribute("storeIntroduction", ownerProfile == null ? "" : ownerProfile.storeIntroduction());
			model.addAttribute("reviews", reviewCards(matchingService.listReviewsForTarget(owner.id())));
			model.addAttribute("reviewSummary", reviewSummary(owner.id()));
			model.addAttribute("ownerId", owner.id());
		}
		catch (Exception ignored) {
			return "redirect:/";
		}
		return "job_detail";
	}

	private static final int JOBS_PAGE_SIZE = 12;

	@GetMapping("/jobs")
	String jobs(
		@RequestParam(defaultValue = "") String keyword,
		@RequestParam(defaultValue = "") String region,
		@RequestParam(defaultValue = "") String category,
		@RequestParam(defaultValue = "0") int page,
		Model model,
		HttpSession session
	) {
		addSessionMember(model, session);
		List<JobListingCard> cards = jobListingCards(openRecruitments());
		if (!keyword.isBlank() || !region.isBlank() || !category.isBlank()) {
			cards = filterJobCards(cards, keyword, region, category);
		}
		int total = cards.size();
		int totalPages = Math.max(1, (int) Math.ceil((double) total / JOBS_PAGE_SIZE));
		int current = Math.max(0, Math.min(page, totalPages - 1));
		List<JobListingCard> pageCards = cards.stream()
			.skip((long) current * JOBS_PAGE_SIZE)
			.limit(JOBS_PAGE_SIZE)
			.toList();
		model.addAttribute("jobCards", pageCards);
		model.addAttribute("totalCount", total);
		model.addAttribute("currentPage", current);
		model.addAttribute("totalPages", totalPages);
		model.addAttribute("keyword", keyword);
		model.addAttribute("region", region);
		model.addAttribute("category", category);
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

	@GetMapping("/login")
	String loginPage(Model model, HttpSession session) {
		addSessionMember(model, session);
		return "login";
	}

	@GetMapping("/login/local")
	String loginLocalFallback() {
		return "redirect:/login";
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
		return "redirect:/";
	}

	@GetMapping("/worker/home")
	String workerHome() {
		return "redirect:/";
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
		@RequestParam(defaultValue = "") String preferredArea,
		@RequestParam int desiredHourlyWage,
		@RequestParam(defaultValue = "") String experiencedIndustries,
		@RequestParam(defaultValue = "false") boolean urgentSubstituteAvailable,
		@RequestParam(defaultValue = "") String introduction,
		@RequestParam(defaultValue = "") String gender,
		@RequestParam(defaultValue = "") String militaryService,
		@RequestParam(defaultValue = "") String education,
		@RequestParam(defaultValue = "") String careerLevel,
		@RequestParam(defaultValue = "") String preferredDays,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MemberSnapshot updated = memberService.updateJobSeekerProfile(
			member.id(),
			availableTime,
			preferredArea,
			desiredHourlyWage,
			splitList(experiencedIndustries),
			urgentSubstituteAvailable,
			introduction,
			gender,
			militaryService,
			education,
			careerLevel,
			splitList(preferredDays),
			null
		);
		setSessionMember(session, updated);
		liveUpdateService.publish("profiles");
		redirectAttributes.addFlashAttribute("successMessage", "이력서가 등록되었습니다!");
		return "redirect:/";
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
		MatchRequestSnapshot updated = matchingService.acceptMatchRequest(id, member.id());
		notificationService.notify(updated.ownerId(), NotificationType.MATCH_ACCEPTED,
			member.displayName() + "님이 제안을 수락했어요", "매칭이 확정되었습니다. 근무를 준비해주세요.",
			"/match-confirmed?matchRequestId=" + id);
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + id;
	}

	@PostMapping("/worker/requests/{id}/decline")
	String declineRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MatchRequestSnapshot updated = matchingService.declineMatchRequest(id, member.id());
		notificationService.notify(updated.ownerId(), NotificationType.MATCH_DECLINED,
			member.displayName() + "님이 제안을 거절했어요", "다른 후보에게 제안해보세요.", "/inbox");
		liveUpdateService.publish("match-requests");
		return "redirect:/inbox";
	}

	@PostMapping("/worker/requests/{id}/cancel")
	String cancelWorkerSentRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.cancelMatchRequestByJobSeeker(id, member.id());
		liveUpdateService.publish("match-requests");
		return "redirect:/inbox";
	}

	@PostMapping("/worker/match-requests")
	String createWorkerMatchRequest(
		@RequestParam UUID ownerId,
		@RequestParam(defaultValue = "근무 지원합니다.") String message,
		HttpSession session
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		MatchRequestSnapshot request = matchingService.createMatchRequestFromJobSeeker(member.id(), ownerId, message);
		notificationService.notify(ownerId, NotificationType.MATCH_REQUEST,
			member.displayName() + "님이 지원했어요", message, "/inbox");
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + request.id();
	}

	@PostMapping("/worker/instant-apply")
	String instantApply(
		@RequestParam UUID ownerId,
		@RequestParam UUID recruitmentId,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		Recruitment recruitment = matchingService.getRecruitment(recruitmentId);
		if (!recruitment.instantHire()) {
			throw ApiException.conflict("NOT_INSTANT_HIRE", "즉시확정 공고가 아닙니다.");
		}
		MatchRequestSnapshot request = matchingService.createMatchRequestFromJobSeeker(
			member.id(), ownerId, "[즉시확정] " + recruitment.title() + " 지원합니다.");
		MatchRequestSnapshot accepted = matchingService.acceptMatchRequestByOwner(request.id(), ownerId);
		notificationService.notify(ownerId, NotificationType.MATCH_ACCEPTED,
			member.displayName() + "님이 즉시확정으로 매칭됐어요", recruitment.title() + " 근무가 확정되었습니다.",
			"/match-confirmed?matchRequestId=" + accepted.id());
		liveUpdateService.publish("match-requests");
		redirectAttributes.addFlashAttribute("successMessage", "즉시 확정되었습니다! 근무를 준비해주세요.");
		return "redirect:/match-confirmed?matchRequestId=" + accepted.id();
	}

	@GetMapping("/substitutes")
	String substitutes(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("openRequests", substituteCards(matchingService.listOpenSubstituteRequests(), member.id()));
		model.addAttribute("myRequests", substituteCards(matchingService.listSubstituteRequestsByRequester(member.id()), member.id()));
		return "substitutes";
	}

	@GetMapping("/substitutes/new")
	String substituteForm(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("shiftOptions", myAcceptedShiftOptions(member.id()));
		return "substitute_new";
	}

	@PostMapping("/substitutes")
	String createSubstitute(
		@RequestParam UUID ownerId,
		@RequestParam(required = false) UUID recruitmentId,
		@RequestParam(defaultValue = "") String storeName,
		@RequestParam(defaultValue = "") String shiftInfo,
		@RequestParam String reason,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		matchingService.createSubstituteRequest(member.id(), ownerId, recruitmentId, storeName, shiftInfo, reason);
		notificationService.notify(ownerId, NotificationType.SUBSTITUTE_REQUEST,
			member.displayName() + "님이 대타를 요청했어요", reason, "/substitutes");
		liveUpdateService.publish("substitutes");
		redirectAttributes.addFlashAttribute("successMessage", "대타 요청을 등록했어요. 대타 마켓에 노출됩니다.");
		return "redirect:/substitutes";
	}

	@PostMapping("/substitutes/{id}/take")
	String takeSubstitute(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
		MemberSnapshot member = ensureRole(session, MemberRole.JOB_SEEKER);
		SubstituteRequest request = matchingService.takeSubstituteRequest(id, member.id());
		notificationService.notify(request.requesterId(), NotificationType.SUBSTITUTE_REQUEST,
			member.displayName() + "님이 대타를 맡아줬어요", request.shiftInfo() + " 대타가 구해졌습니다.", "/substitutes");
		notificationService.notify(request.ownerId(), NotificationType.SUBSTITUTE_REQUEST,
			"대타자가 배정됐어요", member.displayName() + "님이 " + request.shiftInfo() + " 대타를 맡았습니다.", "/inbox");
		liveUpdateService.publish("substitutes");
		redirectAttributes.addFlashAttribute("successMessage", "대타를 맡았어요! 근무를 준비해주세요.");
		return "redirect:/substitutes";
	}

	@GetMapping("/chat/{matchRequestId}")
	String chat(@PathVariable UUID matchRequestId, Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		MatchRequestSnapshot request = matchingService.getMatchRequest(matchRequestId);
		boolean memberIsOwner = request.ownerId().equals(member.id());
		UUID otherId = memberIsOwner ? request.jobSeekerId() : request.ownerId();
		MemberSnapshot other = memberService.getMember(otherId);
		String otherName = memberIsOwner ? other.displayName() : storeName(other);
		List<ChatBubble> messages = matchingService.listChatMessages(matchRequestId, member.id()).stream()
			.map(m -> new ChatBubble(m.senderId().equals(member.id()), m.body(), shortTime(m.createdAt())))
			.toList();
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("matchRequestId", matchRequestId);
		model.addAttribute("otherName", otherName);
		model.addAttribute("messages", messages);
		return "chat";
	}

	@PostMapping("/chat/{matchRequestId}")
	String sendChat(@PathVariable UUID matchRequestId, @RequestParam String body, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		matchingService.sendChatMessage(matchRequestId, member.id(), body);
		MatchRequestSnapshot request = matchingService.getMatchRequest(matchRequestId);
		UUID otherId = request.ownerId().equals(member.id()) ? request.jobSeekerId() : request.ownerId();
		notificationService.notify(otherId, NotificationType.GENERAL,
			member.displayName() + "님의 새 메시지", body, "/chat/" + matchRequestId);
		liveUpdateService.publish("chat");
		return "redirect:/chat/" + matchRequestId;
	}

	private String shortTime(java.time.Instant instant) {
		return java.time.LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault())
			.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd HH:mm"));
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
	String bossHome() {
		return "redirect:/";
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
		memberService.updateOwnerProfile(owner.id(), storeName, storeAddress, businessCategory, storeIntroduction, null);
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
		@RequestParam(defaultValue = "") String tags,
		@RequestParam(defaultValue = "") String description,
		@RequestParam(defaultValue = "false") boolean instantHire,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = requireBusinessVerifiedOwner(session);
		Recruitment created = matchingService.createRecruitment(
			member.id(),
			title,
			workDate,
			startTime,
			endTime,
			workplaceAddress,
			hourlyWage,
			splitList(tags),
			description,
			instantHire
		);
		notifyMatchingSeekers(created, storeName(member));
		liveUpdateService.publish("recruitments");
		redirectAttributes.addFlashAttribute("successMessage", "공고가 등록되었습니다!");
		return "redirect:/";
	}

	private void notifyMatchingSeekers(Recruitment recruitment, String storeName) {
		String category = recruitment.tags().isEmpty() ? "" : String.join(" ", recruitment.tags());
		for (MemberSnapshot seeker : memberService.listJobSeekersWithProfiles()) {
			JobSeekerProfile profile = seeker.jobSeekerProfile();
			if (profile == null) {
				continue;
			}
			String region = regionToken(profile.preferredArea());
			boolean regionMatch = !region.isBlank() && containsIgnoreCase(recruitment.workplaceAddress(), region);
			boolean industryMatch = !profile.experiencedIndustries().isEmpty()
				&& profile.experiencedIndustries().stream()
					.anyMatch(ind -> containsIgnoreCase(recruitment.title() + " " + category, ind));
			if (regionMatch || industryMatch) {
				try {
					notificationService.notify(seeker.id(), NotificationType.NEW_RECOMMENDED_JOB,
						"내 조건에 맞는 새 공고가 올라왔어요",
						storeName + " · " + recruitment.title(),
						"/jobs/" + recruitment.id());
				}
				catch (Exception ignored) {
					// 알림 실패는 무시 (공고 등록 자체는 성공)
				}
			}
		}
	}

	@GetMapping("/boss/recruitments/{id}")
	String recruitmentDetail(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		Recruitment recruitment = matchingService.getRecruitment(id);
		if (!recruitment.ownerId().equals(member.id())) {
			throw ApiException.forbidden("RECRUITMENT_NOT_OWNED", "Only the recruitment owner can view recommendations.");
		}
		List<MatchingRecommendation> recommendations;
		try {
			recommendations = matchingRecommendationService.recommendForRecruitment(id);
		}
		catch (Exception ignored) {
			recommendations = List.of();
		}
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
		notificationService.notify(jobSeekerId, NotificationType.MATCH_REQUEST,
			storeName(owner) + " 사장님이 매칭을 제안했어요", message, "/inbox");
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + request.id();
	}

	@PostMapping("/boss/requests/{id}/accept")
	String acceptWorkerRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot owner = requireBusinessVerifiedOwner(session);
		MatchRequestSnapshot updated = matchingService.acceptMatchRequestByOwner(id, owner.id());
		notificationService.notify(updated.jobSeekerId(), NotificationType.MATCH_ACCEPTED,
			storeName(owner) + " 사장님이 지원을 수락했어요", "매칭이 확정되었습니다. 근무를 준비해주세요.",
			"/match-confirmed?matchRequestId=" + id);
		liveUpdateService.publish("match-requests");
		return "redirect:/match-confirmed?matchRequestId=" + id;
	}

	@PostMapping("/boss/requests/{id}/decline")
	String declineWorkerRequest(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot owner = requireBusinessVerifiedOwner(session);
		MatchRequestSnapshot updated = matchingService.declineMatchRequestByOwner(id, owner.id());
		notificationService.notify(updated.jobSeekerId(), NotificationType.MATCH_DECLINED,
			storeName(owner) + " 사장님이 지원을 거절했어요", "다른 공고에 지원해보세요.", "/");
		liveUpdateService.publish("match-requests");
		return "redirect:/inbox";
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

	@PostMapping("/profile/name")
	String changeDisplayName(
		@RequestParam String displayName,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = requireMember(session);
		MemberSnapshot updated = memberService.changeDisplayName(member.id(), displayName);
		setSessionMember(session, updated);
		redirectAttributes.addFlashAttribute("successMessage", "이름이 변경되었습니다.");
		return "redirect:/mypage";
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
		notificationService.notify(targetId, NotificationType.REVIEW_RECEIVED,
			member.displayName() + "님이 평가를 남겼어요", rating + "점 · " + review, "/mypage");
		liveUpdateService.publish("reviews");
		return "redirect:/mypage";
	}

	@GetMapping("/notifications")
	String notifications(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		List<Notification> items = notificationService.list(member.id());
		model.addAttribute("notifications", items);
		notificationService.markAllRead(member.id());
		return "notifications";
	}

	@PostMapping("/notifications/{id}/read")
	String readNotification(@PathVariable UUID id, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		Notification notification = notificationService.markRead(id, member.id());
		String link = notification.linkUrl();
		return "redirect:" + (link == null || link.isBlank() ? "/notifications" : link);
	}

	@PostMapping("/notifications/read-all")
	String readAllNotifications(HttpSession session) {
		MemberSnapshot member = requireMember(session);
		notificationService.markAllRead(member.id());
		return "redirect:/notifications";
	}

	@GetMapping("/inbox")
	String inbox(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		boolean viewerIsOwner = member.activeRole() == MemberRole.OWNER;
		List<MatchRequestSnapshot> all = viewerIsOwner
			? matchingService.listMatchRequestsForOwner(member.id())
			: matchingService.listMatchRequestsForJobSeeker(member.id());
		MatchRequestInitiator counterpartInitiator = viewerIsOwner
			? MatchRequestInitiator.JOB_SEEKER
			: MatchRequestInitiator.OWNER;
		List<InboxCard> received = all.stream()
			.filter(r -> r.requestedBy() == counterpartInitiator)
			.map(r -> inboxCard(r, viewerIsOwner, true))
			.toList();
		List<InboxCard> sent = all.stream()
			.filter(r -> r.requestedBy() != counterpartInitiator)
			.map(r -> inboxCard(r, viewerIsOwner, false))
			.toList();
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("viewerIsOwner", viewerIsOwner);
		model.addAttribute("receivedRequests", received);
		model.addAttribute("sentRequests", sent);
		return "inbox";
	}

	@GetMapping("/boss/recruitments/manage")
	String manageRecruitments(Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("recruitments", recruitmentCards(matchingService.listRecruitmentsForOwner(member.id())));
		return "boss_manage";
	}

	@PostMapping("/boss/recruitments/{id}/close")
	String closeRecruitment(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		matchingService.closeRecruitment(id, member.id());
		liveUpdateService.publish("recruitments");
		redirectAttributes.addFlashAttribute("successMessage", "공고를 마감했습니다.");
		return "redirect:/boss/recruitments/manage";
	}

	@PostMapping("/boss/recruitments/{id}/delete")
	String deleteRecruitment(@PathVariable UUID id, HttpSession session, RedirectAttributes redirectAttributes) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		matchingService.deleteRecruitment(id, member.id());
		liveUpdateService.publish("recruitments");
		redirectAttributes.addFlashAttribute("successMessage", "공고를 삭제했습니다.");
		return "redirect:/boss/recruitments/manage";
	}

	@GetMapping("/boss/recruitments/{id}/edit")
	String editRecruitmentForm(@PathVariable UUID id, Model model, HttpSession session) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		Recruitment recruitment = matchingService.getRecruitment(id);
		if (!recruitment.ownerId().equals(member.id())) {
			throw ApiException.forbidden("RECRUITMENT_NOT_OWNED", "본인 공고만 수정할 수 있습니다.");
		}
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("recruitment", recruitment);
		return "boss_post_edit";
	}

	@PostMapping("/boss/recruitments/{id}/edit")
	String updateRecruitment(
		@PathVariable UUID id,
		@RequestParam String title,
		@RequestParam LocalDate workDate,
		@RequestParam LocalTime startTime,
		@RequestParam LocalTime endTime,
		@RequestParam String workplaceAddress,
		@RequestParam int hourlyWage,
		@RequestParam(defaultValue = "") String tags,
		@RequestParam(defaultValue = "") String description,
		@RequestParam(defaultValue = "false") boolean instantHire,
		HttpSession session,
		RedirectAttributes redirectAttributes
	) {
		MemberSnapshot member = ensureRole(session, MemberRole.OWNER);
		matchingService.updateRecruitment(id, member.id(), title, workDate, startTime, endTime,
			workplaceAddress, hourlyWage, splitList(tags), description, instantHire);
		liveUpdateService.publish("recruitments");
		redirectAttributes.addFlashAttribute("successMessage", "공고를 수정했습니다.");
		return "redirect:/boss/recruitments/manage";
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
		if (requestUri != null && requestUri.startsWith("/login")) {
			return "redirect:/login";
		}
		return "redirect:/";
	}

	@ExceptionHandler(Exception.class)
	String handleUnexpectedException(
		Exception exception,
		HttpServletRequest request,
		RedirectAttributes redirectAttributes
	) {
		redirectAttributes.addFlashAttribute("errorMessage", "오류가 발생했습니다: " + exception.getMessage());
		return "redirect:/";
	}

	@org.springframework.web.bind.annotation.ModelAttribute
	void injectNotificationBadge(Model model, HttpSession session) {
		long unread = currentMember(session).map(m -> notificationService.unreadCount(m.id())).orElse(0L);
		model.addAttribute("unreadNotifications", unread);
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
		return "/";
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

	private List<SubstituteCard> substituteCards(List<SubstituteRequest> requests, UUID viewerId) {
		return requests.stream()
			.map(request -> {
				String requesterName;
				try {
					requesterName = memberService.getMember(request.requesterId()).displayName();
				}
				catch (Exception ignored) {
					requesterName = "구직자";
				}
				boolean mine = request.requesterId().equals(viewerId);
				boolean canTake = request.isOpen() && !mine;
				return new SubstituteCard(
					request.id(),
					requesterName,
					request.storeName() == null || request.storeName().isBlank() ? "매장" : request.storeName(),
					request.shiftInfo() == null ? "" : request.shiftInfo(),
					request.reason(),
					substituteStatusLabel(request.status().name()),
					request.status().name(),
					canTake,
					mine
				);
			})
			.toList();
	}

	private List<ShiftOption> myAcceptedShiftOptions(UUID memberId) {
		return matchingService.listAcceptedMatchRequestsForParticipant(memberId).stream()
			.filter(request -> request.jobSeekerId().equals(memberId))
			.map(request -> {
				MemberSnapshot owner = memberService.getMember(request.ownerId());
				String shiftInfo;
				if (request.recruitmentId() != null) {
					try {
						Recruitment recruitment = matchingService.getRecruitment(request.recruitmentId());
						shiftInfo = recruitment.title() + " (" + recruitment.workTime() + ")";
					}
					catch (Exception ignored) {
						shiftInfo = request.message();
					}
				}
				else {
					shiftInfo = request.message();
				}
				return new ShiftOption(request.ownerId(), request.recruitmentId(), storeName(owner), shiftInfo);
			})
			.toList();
	}

	private String substituteStatusLabel(String status) {
		return switch (status) {
			case "OPEN" -> "대타 구함";
			case "FILLED" -> "대타 구함 완료";
			case "CANCELED" -> "취소됨";
			default -> status;
		};
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
			statusLabel(recruitment.status().name()),
			recruitment.isOpen()
		);
	}

	private InboxCard inboxCard(MatchRequestSnapshot request, boolean viewerIsOwner, boolean received) {
		UUID counterpartId = viewerIsOwner ? request.jobSeekerId() : request.ownerId();
		MemberSnapshot counterpart = memberService.getMember(counterpartId);
		String name = viewerIsOwner ? counterpart.displayName() : storeName(counterpart);
		String context = "";
		if (request.recruitmentId() != null) {
			try {
				context = matchingService.getRecruitment(request.recruitmentId()).title();
			}
			catch (Exception ignored) {
				context = "";
			}
		}
		boolean actionable = received && request.status() == MatchRequestStatus.REQUESTED;
		boolean cancelable = !received && request.status() == MatchRequestStatus.REQUESTED;
		return new InboxCard(
			request.id(),
			name,
			request.message(),
			context,
			statusLabel(request.status().name()),
			request.status().name(),
			actionable,
			cancelable
		);
	}

	private List<Recruitment> openRecruitments() {
		return matchingService.listRecruitments().stream()
			.filter(Recruitment::isOpen)
			.toList();
	}

	private List<ProfileCard> profileCards() {
		return memberService.listJobSeekersWithProfiles().stream()
			.map(this::profileCard)
			.toList();
	}

	private ProfileCard profileCard(MemberSnapshot member) {
		JobSeekerProfile profile = member.jobSeekerProfile();
		String industries = profile.experiencedIndustries().isEmpty()
			? "경력 미입력"
			: String.join(", ", profile.experiencedIndustries());
		return new ProfileCard(
			member.id(),
			member.displayName(),
			profile.preferredArea() == null || profile.preferredArea().isBlank() ? "지역 미설정" : profile.preferredArea(),
			industries,
			profile.availableTime(),
			profile.desiredHourlyWage(),
			profile.careerLevel() == null || profile.careerLevel().isBlank() ? "" : profile.careerLevel(),
			profile.education() == null || profile.education().isBlank() ? "" : profile.education(),
			profile.gender() == null || profile.gender().isBlank() ? "" : profile.gender(),
			profile.militaryService() == null || profile.militaryService().isBlank() ? "" : profile.militaryService(),
			profile.imageUrl() == null ? "" : profile.imageUrl()
		);
	}

	private List<JobListingCard> jobListingCards(List<Recruitment> recruitments) {
		return recruitments.stream()
			.flatMap(r -> {
				try {
					return java.util.stream.Stream.of(jobListingCard(r));
				}
				catch (Exception ignored) {
					return java.util.stream.Stream.empty();
				}
			})
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
			statusLabel(recruitment.status().name()),
			recruitment.tags(),
			countdownLabel(recruitment.workDate(), recruitment.startTime()),
			isUrgent(recruitment.workDate(), recruitment.startTime()),
			recruitment.instantHire(),
			recruitment.ownerId(),
			profile == null || profile.imageUrl() == null ? "" : profile.imageUrl()
		);
	}

	private String countdownLabel(LocalDate workDate, LocalTime startTime) {
		java.time.LocalDateTime start = java.time.LocalDateTime.of(workDate, startTime);
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		if (!start.isAfter(now)) {
			return "";
		}
		java.time.Duration d = java.time.Duration.between(now, start);
		long minutes = d.toMinutes();
		if (minutes < 60) {
			return minutes + "분 후 시작";
		}
		long hours = d.toHours();
		if (hours < 24) {
			return hours + "시간 후 시작";
		}
		return d.toDays() + "일 후 시작";
	}

	private boolean isUrgent(LocalDate workDate, LocalTime startTime) {
		java.time.LocalDateTime start = java.time.LocalDateTime.of(workDate, startTime);
		java.time.LocalDateTime now = java.time.LocalDateTime.now();
		return start.isAfter(now) && java.time.Duration.between(now, start).toHours() < 24;
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

	private List<JobListingCard> filterJobCards(List<JobListingCard> cards, String keyword, String region, String category) {
		return cards.stream()
			.filter(c -> containsIgnoreCase(c.title() + " " + c.storeName() + " " + c.workTime() + " " + String.join(" ", c.tags()), keyword))
			.filter(c -> containsIgnoreCase(c.workplaceAddress(), region))
			.filter(c -> containsIgnoreCase(c.businessCategory(), category))
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

	record RecruitmentCard(UUID id, String title, String workTime, String workplaceAddress, int hourlyWage, String status, boolean open) {
	}

	record InboxCard(UUID id, String counterpartName, String message, String context, String status, String statusCode, boolean actionable, boolean cancelable) {
	}

	record SubstituteCard(UUID id, String requesterName, String storeName, String shiftInfo, String reason, String status, String statusCode, boolean canTake, boolean mine) {
	}

	record ShiftOption(UUID ownerId, UUID recruitmentId, String storeName, String shiftInfo) {
	}

	record ChatBubble(boolean mine, String body, String time) {
	}

	record JobListingCard(UUID id, String title, String storeName, String businessCategory, String workplaceAddress, String workTime, int hourlyWage, String status, List<String> tags, String countdown, boolean urgent, boolean instantHire, UUID ownerId, String storeImageUrl) {
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

	record ProfileCard(UUID id, String name, String area, String industries, String availableTime, int desiredHourlyWage, String careerLevel, String education, String gender, String militaryService, String imageUrl) {
	}
}
