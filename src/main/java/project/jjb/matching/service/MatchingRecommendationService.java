package project.jjb.matching.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import project.jjb.matching.domain.HomeRecommendation;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.MatchRequestStatus;
import project.jjb.matching.domain.MatchingRecommendation;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.Review;
import project.jjb.member.domain.JobSeekerProfile;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.domain.OwnerProfile;
import project.jjb.member.service.MemberService;

@Service
public class MatchingRecommendationService {

	private final MatchingService matchingService;
	private final MemberService memberService;
	private final ObjectProvider<ChatModel> chatModelProvider;

	public MatchingRecommendationService(
		MatchingService matchingService,
		MemberService memberService,
		ObjectProvider<ChatModel> chatModelProvider
	) {
		this.matchingService = matchingService;
		this.memberService = memberService;
		this.chatModelProvider = chatModelProvider;
	}

	@Transactional(readOnly = true)
	public List<MatchingRecommendation> recommendForRecruitment(UUID recruitmentId) {
		Recruitment recruitment = matchingService.getRecruitment(recruitmentId);
		return memberService.listJobSeekersWithProfiles().stream()
			.filter(candidate -> !candidate.id().equals(recruitment.ownerId()))
			.map(candidate -> recommendation(recruitment, candidate))
			.sorted(Comparator.comparingInt(MatchingRecommendation::score).reversed()
				.thenComparing(MatchingRecommendation::displayName))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<HomeRecommendation> recommendStoresForJobSeeker(UUID jobSeekerId) {
		memberService.requireJobSeekerReady(jobSeekerId);
		MemberSnapshot jobSeeker = memberService.getMember(jobSeekerId);
		JobSeekerProfile profile = jobSeeker.jobSeekerProfile();
		return memberService.listVerifiedOwnersWithProfiles().stream()
			.filter(owner -> !owner.id().equals(jobSeekerId))
			.map(owner -> storeRecommendation(owner, profile))
			.sorted(Comparator.comparingInt(HomeRecommendation::score).reversed()
				.thenComparing(HomeRecommendation::targetName))
			.toList();
	}

	@Transactional(readOnly = true)
	public List<HomeRecommendation> recommendCandidatesForOwner(UUID ownerId) {
		memberService.requireOwnerReady(ownerId);
		MemberSnapshot owner = memberService.getMember(ownerId);
		OwnerProfile ownerProfile = owner.ownerProfile();
		return memberService.listJobSeekersWithProfiles().stream()
			.filter(candidate -> !candidate.id().equals(ownerId))
			.map(candidate -> candidateRecommendation(candidate, ownerProfile))
			.sorted(Comparator.comparingInt(HomeRecommendation::score).reversed()
				.thenComparing(HomeRecommendation::targetName))
			.toList();
	}

	private MatchingRecommendation recommendation(Recruitment recruitment, MemberSnapshot candidate) {
		JobSeekerProfile profile = candidate.jobSeekerProfile();
		int score = score(recruitment, profile);
		String baseReason = reason(recruitment, profile, score);
		String aiReason = aiReason(recruitment, candidate, score).orElse(baseReason);
		return new MatchingRecommendation(
			candidate.id(),
			candidate.displayName(),
			score,
			aiReason,
			profileSummary(profile)
		);
	}

	private HomeRecommendation storeRecommendation(MemberSnapshot owner, JobSeekerProfile jobSeekerProfile) {
		OwnerProfile ownerProfile = owner.ownerProfile();
		int score = 45;
		if (owner.verification().businessVerified()) {
			score += 15;
		}
		if (ownerProfile != null && containsOverlap(jobSeekerProfile.preferredArea(), ownerProfile.storeAddress())) {
			score += 20;
		}
		if (ownerProfile != null && isEasyWork(ownerProfile.storeIntroduction())) {
			score += 10;
		}
		double averageRating = averageRating(owner.id());
		if (averageRating >= 4.5) {
			score += 10;
		}
		score -= trustPenalty(owner.id());
		score = Math.max(0, Math.min(score, 100));
		String baseReason = storeReason(ownerProfile, averageRating, score);
		String aiReason = homeAiReason("""
			추천대상: 가게
			가게명: %s
			주소: %s
			업종: %s
			가게소개: %s
			구직자 희망지역: %s
			구직자 가능시간: %s
			구직자 경험: %s
			후기평점: %s
			취소/거절 패널티: %d
			규칙점수: %d
			기본추천사유: %s
			""".formatted(
				storeName(owner),
				ownerProfile == null ? "" : ownerProfile.storeAddress(),
				ownerProfile == null ? "" : ownerProfile.businessCategory(),
				ownerProfile == null ? "" : ownerProfile.storeIntroduction(),
				jobSeekerProfile.preferredArea(),
				jobSeekerProfile.availableTime(),
				String.join(", ", jobSeekerProfile.experiencedIndustries()),
				ratingLabel(averageRating),
				trustPenalty(owner.id()),
				score,
				baseReason
			)).orElse(baseReason);
		return new HomeRecommendation(
			owner.id(),
			storeName(owner),
			"OWNER",
			score,
			aiReason,
			storeSummary(ownerProfile, averageRating)
		);
	}

	private HomeRecommendation candidateRecommendation(MemberSnapshot candidate, OwnerProfile ownerProfile) {
		JobSeekerProfile profile = candidate.jobSeekerProfile();
		int score = 35;
		if (profile.urgentSubstituteAvailable()) {
			score += 15;
		}
		if (ownerProfile != null && containsOverlap(profile.preferredArea(), ownerProfile.storeAddress())) {
			score += 20;
		}
		if (ownerProfile != null && containsAny(ownerProfile.businessCategory() + " " + ownerProfile.storeIntroduction(), profile.experiencedIndustries())) {
			score += 15;
		}
		if (!profile.availableTime().isBlank()) {
			score += 10;
		}
		double averageRating = averageRating(candidate.id());
		if (averageRating >= 4.5) {
			score += 10;
		}
		score -= trustPenalty(candidate.id());
		score = Math.max(0, Math.min(score, 100));
		String baseReason = candidateReason(profile, averageRating, score);
		String aiReason = homeAiReason("""
			추천대상: 구직자
			구직자명: %s
			가능시간: %s
			희망지역: %s
			희망시급: %d
			경험: %s
			긴급대타가능: %s
			매장주소: %s
			매장업종: %s
			매장소개: %s
			후기평점: %s
			취소/거절 패널티: %d
			규칙점수: %d
			기본추천사유: %s
			""".formatted(
				candidate.displayName(),
				profile.availableTime(),
				profile.preferredArea(),
				profile.desiredHourlyWage(),
				String.join(", ", profile.experiencedIndustries()),
				profile.urgentSubstituteAvailable() ? "예" : "아니오",
				ownerProfile == null ? "" : ownerProfile.storeAddress(),
				ownerProfile == null ? "" : ownerProfile.businessCategory(),
				ownerProfile == null ? "" : ownerProfile.storeIntroduction(),
				ratingLabel(averageRating),
				trustPenalty(candidate.id()),
				score,
				baseReason
			)).orElse(baseReason);
		return new HomeRecommendation(
			candidate.id(),
			candidate.displayName(),
			"JOB_SEEKER",
			score,
			aiReason,
			profileSummary(profile)
		);
	}

	private int score(Recruitment recruitment, JobSeekerProfile profile) {
		int score = 35;
		if (profile.urgentSubstituteAvailable()) {
			score += 15;
		}
		if (containsOverlap(profile.preferredArea(), recruitment.workplaceAddress())) {
			score += 20;
		}
		if (profile.desiredHourlyWage() <= recruitment.hourlyWage()) {
			score += 15;
		}
		if (containsAny(recruitment.title(), profile.experiencedIndustries())) {
			score += 15;
		}
		if (containsOverlap(profile.availableTime(), recruitment.workTime())) {
			score += 10;
		}
		return Math.min(score, 100);
	}

	private String reason(Recruitment recruitment, JobSeekerProfile profile, int score) {
		if (score >= 80) {
			return "지역, 시급, 경험 조건이 모집과 잘 맞습니다.";
		}
		if (profile.urgentSubstituteAvailable()) {
			return "긴급 대타 가능 상태라 빠른 제안에 적합합니다.";
		}
		if (profile.desiredHourlyWage() <= recruitment.hourlyWage()) {
			return "희망 시급이 모집 시급 범위 안에 있습니다.";
		}
		return "등록된 프로필 기준으로 기본 조건을 충족합니다.";
	}

	private Optional<String> aiReason(Recruitment recruitment, MemberSnapshot candidate, int score) {
		return chatReason("""
			모집: %s, 근무: %s, 위치: %s, 시급: %d원
			구직자: %s, 가능시간: %s, 희망지역: %s, 희망시급: %d원, 경험: %s, 규칙점수: %d
			""".formatted(
				recruitment.title(),
				recruitment.workTime(),
				recruitment.workplaceAddress(),
				recruitment.hourlyWage(),
				candidate.displayName(),
				candidate.jobSeekerProfile().availableTime(),
				candidate.jobSeekerProfile().preferredArea(),
				candidate.jobSeekerProfile().desiredHourlyWage(),
				String.join(", ", candidate.jobSeekerProfile().experiencedIndustries()),
				score
			));
	}

	private Optional<String> homeAiReason(String context) {
		return chatReason(context);
	}

	private Optional<String> chatReason(String context) {
		ChatModel chatModel = chatModelProvider.getIfAvailable();
		if (chatModel == null) {
			return Optional.empty();
		}
		try {
			String content = ChatClient.create(chatModel)
				.prompt()
				.system("You write concise Korean matching reasons for a short-term job matching service. Return one sentence only. Do not invent facts beyond the provided context.")
				.user(context)
				.call()
				.content();
			if (content == null || content.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(content.trim());
		}
		catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private String profileSummary(JobSeekerProfile profile) {
		String industries = profile.experiencedIndustries().isEmpty()
			? "경험 미입력"
			: String.join(", ", profile.experiencedIndustries());
		return "%s · %s · 희망 %d원 · %s".formatted(
			profile.availableTime(),
			profile.preferredArea(),
			profile.desiredHourlyWage(),
			industries
		);
	}

	private String storeSummary(OwnerProfile profile, double averageRating) {
		if (profile == null) {
			return "가게 프로필 미입력 · 후기 " + ratingLabel(averageRating);
		}
		return "%s · %s · 후기 %s".formatted(
			profile.storeAddress(),
			profile.businessCategory(),
			ratingLabel(averageRating)
		);
	}

	private String storeReason(OwnerProfile profile, double averageRating, int score) {
		if (score >= 80) {
			return "희망 지역과 가게 신뢰 지표가 잘 맞습니다.";
		}
		if (averageRating >= 4.5) {
			return "후기 평점이 높아 안정적인 근무처로 추천합니다.";
		}
		if (profile != null && isEasyWork(profile.storeIntroduction())) {
			return "업무 난이도가 낮아 보이는 설명이 있어 부담이 적습니다.";
		}
		return "사업자 인증과 가게 프로필을 기준으로 추천합니다.";
	}

	private String candidateReason(JobSeekerProfile profile, double averageRating, int score) {
		if (score >= 80) {
			return "지역, 가능 시간, 신뢰 지표가 매장 조건과 잘 맞습니다.";
		}
		if (profile.urgentSubstituteAvailable()) {
			return "긴급 대타 가능 상태라 빠른 제안에 적합합니다.";
		}
		if (averageRating >= 4.5) {
			return "후기 평점이 높아 우선 검토하기 좋습니다.";
		}
		return "등록 프로필과 MVP 신뢰 기록을 기준으로 추천합니다.";
	}

	private String storeName(MemberSnapshot owner) {
		OwnerProfile profile = owner.ownerProfile();
		if (profile != null && profile.storeName() != null && !profile.storeName().isBlank()) {
			return profile.storeName();
		}
		return owner.displayName();
	}

	private double averageRating(UUID targetId) {
		List<Review> reviews = matchingService.listReviewsForTarget(targetId);
		return reviews.stream()
			.mapToInt(Review::rating)
			.average()
			.orElse(0.0);
	}

	private int trustPenalty(UUID memberId) {
		long count = matchingService.listMatchRequestsForParticipant(memberId).stream()
			.map(MatchRequestSnapshot::status)
			.filter(status -> status == MatchRequestStatus.CANCELED || status == MatchRequestStatus.DECLINED)
			.count();
		return (int) Math.min(count * 5, 15);
	}

	private String ratingLabel(double averageRating) {
		return averageRating == 0.0 ? "없음" : "%.1f점".formatted(averageRating);
	}

	private boolean isEasyWork(String value) {
		String normalized = normalize(value);
		return normalized.contains("쉬") || normalized.contains("초보") || normalized.contains("간단") || normalized.contains("easy");
	}

	private boolean containsAny(String source, List<String> candidates) {
		String normalizedSource = normalize(source);
		return candidates.stream()
			.map(this::normalize)
			.filter(value -> !value.isBlank())
			.anyMatch(normalizedSource::contains);
	}

	private boolean containsOverlap(String left, String right) {
		String normalizedRight = normalize(right);
		for (String token : normalize(left).split("\\s+")) {
			if (token.length() >= 2 && normalizedRight.contains(token)) {
				return true;
			}
		}
		return false;
	}

	private String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replace(",", " ").replace("·", " ").trim();
	}
}
