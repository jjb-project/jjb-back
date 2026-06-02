package project.jjb.matching.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.common.ApiException;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.MatchRequestInitiator;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.MatchRequestStatus;
import project.jjb.matching.domain.Review;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.RecruitmentStatus;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.member.service.MemberService;

@Service
public class MatchingService {

	private static final Pattern TIME_RANGE_PATTERN = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})");

	private final MatchingRepository matchingRepository;
	private final MemberService memberService;

	public MatchingService(MatchingRepository matchingRepository, MemberService memberService) {
		this.matchingRepository = matchingRepository;
		this.memberService = memberService;
	}

	@Transactional
	public Recruitment createRecruitment(
		UUID ownerId,
		String title,
		LocalDate workDate,
		LocalTime startTime,
		LocalTime endTime,
		String workplaceAddress,
		int hourlyWage
	) {
		memberService.requireOwnerReady(ownerId);
		validateRecruitment(title, workDate, startTime, endTime, workplaceAddress, hourlyWage);
		Recruitment recruitment = new Recruitment(
			UUID.randomUUID(),
			ownerId,
			title.trim(),
			workDate,
			startTime,
			endTime,
			workplaceAddress.trim(),
			hourlyWage,
			RecruitmentStatus.OPEN,
			Instant.now()
		);
		return matchingRepository.saveRecruitment(recruitment);
	}

	@Transactional
	public Recruitment createRecruitment(
		UUID ownerId,
		String title,
		String workTime,
		String workplaceAddress,
		int hourlyWage
	) {
		WorkSchedule schedule = parseWorkSchedule(workTime);
		return createRecruitment(ownerId, title, schedule.workDate(), schedule.startTime(), schedule.endTime(), workplaceAddress, hourlyWage);
	}

	@Transactional(readOnly = true)
	public List<Recruitment> listRecruitments() {
		return matchingRepository.findRecruitments();
	}

	@Transactional(readOnly = true)
	public List<Recruitment> listRecruitmentsForOwner(UUID ownerId) {
		return matchingRepository.findRecruitmentsByOwnerId(ownerId);
	}

	@Transactional(readOnly = true)
	public Recruitment getRecruitment(UUID recruitmentId) {
		return matchingRepository.findRecruitmentById(recruitmentId)
			.orElseThrow(() -> ApiException.notFound("RECRUITMENT_NOT_FOUND", "Recruitment was not found."));
	}

	@Transactional
	public MatchRequestSnapshot createMatchRequest(UUID ownerId, UUID jobSeekerId, UUID recruitmentId, String message) {
		memberService.requireOwnerReady(ownerId);
		if (ownerId.equals(jobSeekerId)) {
			throw ApiException.conflict("SELF_MATCH_NOT_ALLOWED", "Owner cannot request a match from themselves.");
		}
		memberService.requireJobSeekerReady(jobSeekerId);
		validateMatchMessage(message);
		if (recruitmentId != null) {
			Recruitment recruitment = getRecruitment(recruitmentId);
			if (!ownerId.equals(recruitment.ownerId())) {
				throw ApiException.forbidden("RECRUITMENT_NOT_OWNED", "Only the recruitment owner can request matches.");
			}
			if (!recruitment.isOpen()) {
				throw ApiException.conflict("RECRUITMENT_NOT_OPEN", "Only open recruitments can receive match requests.");
			}
		}
		boolean duplicateOpenRequest = matchingRepository.findMatchRequestsByOwnerId(ownerId).stream()
			.anyMatch(request -> request.jobSeekerId().equals(jobSeekerId)
				&& sameRecruitment(request.recruitmentId(), recruitmentId)
				&& (request.status() == MatchRequestStatus.REQUESTED || request.status() == MatchRequestStatus.ACCEPTED));
		if (duplicateOpenRequest) {
			throw ApiException.conflict("MATCH_REQUEST_ALREADY_EXISTS", "A match request already exists for this candidate.");
		}
		MatchRequest matchRequest = new MatchRequest(UUID.randomUUID(), ownerId, jobSeekerId, recruitmentId, message.trim());
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot createMatchRequestFromJobSeeker(UUID jobSeekerId, UUID ownerId, String message) {
		memberService.requireJobSeekerReady(jobSeekerId);
		memberService.requireOwnerReady(ownerId);
		if (ownerId.equals(jobSeekerId)) {
			throw ApiException.conflict("SELF_MATCH_NOT_ALLOWED", "Job seeker cannot request a match from themselves.");
		}
		validateMatchMessage(message);
		boolean duplicateOpenRequest = matchingRepository.findMatchRequestsByOwnerId(ownerId).stream()
			.anyMatch(request -> request.jobSeekerId().equals(jobSeekerId)
				&& request.recruitmentId() == null
				&& (request.status() == MatchRequestStatus.REQUESTED || request.status() == MatchRequestStatus.ACCEPTED));
		if (duplicateOpenRequest) {
			throw ApiException.conflict("MATCH_REQUEST_ALREADY_EXISTS", "A match request already exists for this owner.");
		}
		MatchRequest matchRequest = new MatchRequest(
			UUID.randomUUID(),
			ownerId,
			jobSeekerId,
			null,
			message.trim(),
			MatchRequestInitiator.JOB_SEEKER
		);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional(readOnly = true)
	public MatchRequestSnapshot getMatchRequest(UUID matchRequestId) {
		return matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."))
			.snapshot();
	}

	@Transactional(readOnly = true)
	public List<MatchRequestSnapshot> listMatchRequestsForJobSeeker(UUID jobSeekerId) {
		return matchingRepository.findMatchRequestsByJobSeekerId(jobSeekerId).stream()
			.map(MatchRequest::snapshot)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MatchRequestSnapshot> listMatchRequestsForOwner(UUID ownerId) {
		return matchingRepository.findMatchRequestsByOwnerId(ownerId).stream()
			.map(MatchRequest::snapshot)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MatchRequestSnapshot> listMatchRequestsForParticipant(UUID memberId) {
		return matchingRepository.findMatchRequestsByParticipantId(memberId).stream()
			.map(MatchRequest::snapshot)
			.toList();
	}

	@Transactional(readOnly = true)
	public List<MatchRequestSnapshot> listAcceptedMatchRequestsForParticipant(UUID memberId) {
		return matchingRepository.findMatchRequestsByParticipantId(memberId).stream()
			.filter(request -> request.status() == MatchRequestStatus.ACCEPTED)
			.map(MatchRequest::snapshot)
			.toList();
	}

	@Transactional
	public MatchRequestSnapshot acceptMatchRequest(UUID matchRequestId, UUID jobSeekerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		memberService.requireJobSeekerReady(jobSeekerId);
		matchRequest.accept(jobSeekerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot declineMatchRequest(UUID matchRequestId, UUID jobSeekerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		matchRequest.decline(jobSeekerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot acceptMatchRequestByOwner(UUID matchRequestId, UUID ownerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		memberService.requireOwnerReady(ownerId);
		matchRequest.acceptByOwner(ownerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot declineMatchRequestByOwner(UUID matchRequestId, UUID ownerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		memberService.requireOwnerReady(ownerId);
		matchRequest.declineByOwner(ownerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot cancelMatchRequestByJobSeeker(UUID matchRequestId, UUID jobSeekerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		memberService.requireJobSeekerReady(jobSeekerId);
		matchRequest.cancelByJobSeeker(jobSeekerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public MatchRequestSnapshot cancelMatchRequest(UUID matchRequestId, UUID ownerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		matchRequest.cancel(ownerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}

	@Transactional
	public Review createReview(UUID matchRequestId, UUID evaluatorId, UUID targetId, int rating, String reviewText) {
		if (rating < 1 || rating > 5) {
			throw ApiException.badRequest("INVALID_REVIEW_RATING", "Rating must be between 1 and 5.");
		}
		if (reviewText == null || reviewText.isBlank()) {
			throw ApiException.badRequest("INVALID_REVIEW_TEXT", "Review text is required.");
		}
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		if (matchRequest.status() != MatchRequestStatus.ACCEPTED) {
			throw ApiException.conflict("MATCH_NOT_ACCEPTED", "Only accepted matches can be reviewed.");
		}
		boolean evaluatorIsOwner = matchRequest.ownerId().equals(evaluatorId);
		boolean evaluatorIsJobSeeker = matchRequest.jobSeekerId().equals(evaluatorId);
		if (!evaluatorIsOwner && !evaluatorIsJobSeeker) {
			throw ApiException.forbidden("REVIEWER_NOT_PARTICIPANT", "Only match participants can write reviews.");
		}
		UUID expectedTargetId = evaluatorIsOwner ? matchRequest.jobSeekerId() : matchRequest.ownerId();
		if (!expectedTargetId.equals(targetId)) {
			throw ApiException.forbidden("REVIEW_TARGET_NOT_PARTICIPANT", "Review target must be the other match participant.");
		}
		if (matchingRepository.existsReviewByMatchRequestIdAndEvaluatorId(matchRequestId, evaluatorId)) {
			throw ApiException.conflict("REVIEW_ALREADY_EXISTS", "Review already exists for this match.");
		}
		Review review = new Review(UUID.randomUUID(), matchRequestId, evaluatorId, targetId, rating, reviewText.trim(), Instant.now());
		return matchingRepository.saveReview(review);
	}

	@Transactional(readOnly = true)
	public List<Review> listReviewsForTarget(UUID targetId) {
		return matchingRepository.findReviewsByTargetId(targetId);
	}

	private void validateRecruitment(
		String title,
		LocalDate workDate,
		LocalTime startTime,
		LocalTime endTime,
		String workplaceAddress,
		int hourlyWage
	) {
		if (title == null || title.isBlank()) {
			throw ApiException.badRequest("INVALID_RECRUITMENT_TITLE", "Recruitment title is required.");
		}
		if (workDate == null || startTime == null || endTime == null || !endTime.isAfter(startTime)) {
			throw ApiException.badRequest("INVALID_RECRUITMENT_TIME", "Valid work date and time range are required.");
		}
		if (workplaceAddress == null || workplaceAddress.isBlank()) {
			throw ApiException.badRequest("INVALID_RECRUITMENT_ADDRESS", "Workplace address is required.");
		}
		if (hourlyWage <= 0) {
			throw ApiException.badRequest("INVALID_HOURLY_WAGE", "Hourly wage must be positive.");
		}
	}

	private void validateMatchMessage(String message) {
		if (message == null || message.isBlank()) {
			throw ApiException.badRequest("INVALID_MATCH_MESSAGE", "Match request message is required.");
		}
	}

	private WorkSchedule parseWorkSchedule(String workTime) {
		if (workTime == null || workTime.isBlank()) {
			throw ApiException.badRequest("INVALID_RECRUITMENT_TIME", "Work time is required.");
		}
		LocalDate workDate = LocalDate.now();
		String normalized = workTime.trim();
		String[] parts = normalized.split("\\s+", 2);
		if (parts.length == 2) {
			try {
				workDate = LocalDate.parse(parts[0]);
				normalized = parts[1];
			}
			catch (RuntimeException ignored) {
				normalized = workTime.trim();
			}
		}
		Matcher matcher = TIME_RANGE_PATTERN.matcher(normalized);
		if (!matcher.find()) {
			throw ApiException.badRequest("INVALID_RECRUITMENT_TIME", "Work time must include a start and end time.");
		}
		return new WorkSchedule(workDate, LocalTime.parse(padTime(matcher.group(1))), LocalTime.parse(padTime(matcher.group(2))));
	}

	private String padTime(String time) {
		String[] parts = time.split(":");
		return parts[0].length() == 1 ? "0" + time : time;
	}

	private boolean sameRecruitment(UUID left, UUID right) {
		return left == null ? right == null : left.equals(right);
	}

	private record WorkSchedule(LocalDate workDate, LocalTime startTime, LocalTime endTime) {
	}
}
