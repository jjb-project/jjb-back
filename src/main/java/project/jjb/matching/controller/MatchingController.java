package project.jjb.matching.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.jjb.matching.domain.HomeRecommendation;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.MatchingRecommendation;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.Review;
import project.jjb.matching.service.MatchingRecommendationService;
import project.jjb.matching.service.MatchingService;
import project.jjb.web.LiveUpdateService;

@RestController
@RequestMapping("/api")
public class MatchingController {

	private final MatchingService matchingService;
	private final MatchingRecommendationService matchingRecommendationService;
	private final LiveUpdateService liveUpdateService;

	public MatchingController(
		MatchingService matchingService,
		MatchingRecommendationService matchingRecommendationService,
		LiveUpdateService liveUpdateService
	) {
		this.matchingService = matchingService;
		this.matchingRecommendationService = matchingRecommendationService;
		this.liveUpdateService = liveUpdateService;
	}

	@PostMapping("/recruitments")
	@ResponseStatus(HttpStatus.CREATED)
	Recruitment createRecruitment(@Valid @RequestBody CreateRecruitmentRequest request) {
		Recruitment recruitment;
		if (request.workDate() != null && request.startTime() != null && request.endTime() != null) {
			recruitment = matchingService.createRecruitment(
				request.ownerId(),
				request.title(),
				request.workDate(),
				request.startTime(),
				request.endTime(),
				request.workplaceAddress(),
				request.hourlyWage()
			);
		}
		else {
			recruitment = matchingService.createRecruitment(
				request.ownerId(),
				request.title(),
				request.workTime(),
				request.workplaceAddress(),
				request.hourlyWage()
			);
		}
		liveUpdateService.publish("recruitments");
		return recruitment;
	}

	@GetMapping("/recruitments/{recruitmentId}/recommendations")
	List<MatchingRecommendation> recommendCandidates(@PathVariable UUID recruitmentId) {
		return matchingRecommendationService.recommendForRecruitment(recruitmentId);
	}

	@GetMapping("/members/{jobSeekerId}/store-recommendations")
	List<HomeRecommendation> recommendStores(@PathVariable UUID jobSeekerId) {
		return matchingRecommendationService.recommendStoresForJobSeeker(jobSeekerId);
	}

	@GetMapping("/members/{ownerId}/candidate-recommendations")
	List<HomeRecommendation> recommendCandidatesForOwner(@PathVariable UUID ownerId) {
		return matchingRecommendationService.recommendCandidatesForOwner(ownerId);
	}

	@PostMapping("/match-requests")
	@ResponseStatus(HttpStatus.CREATED)
	MatchRequestSnapshot createMatchRequest(@Valid @RequestBody CreateMatchRequestRequest request) {
		MatchRequestSnapshot snapshot = matchingService.createMatchRequest(
			request.ownerId(),
			request.jobSeekerId(),
			request.recruitmentId(),
			request.message()
		);
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/from-job-seeker")
	@ResponseStatus(HttpStatus.CREATED)
	MatchRequestSnapshot createMatchRequestFromJobSeeker(@Valid @RequestBody CreateJobSeekerMatchRequest request) {
		MatchRequestSnapshot snapshot = matchingService.createMatchRequestFromJobSeeker(
			request.jobSeekerId(),
			request.ownerId(),
			request.message()
		);
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/{matchRequestId}/accept")
	MatchRequestSnapshot acceptMatchRequest(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody JobSeekerMatchActionRequest request
	) {
		MatchRequestSnapshot snapshot = matchingService.acceptMatchRequest(matchRequestId, request.jobSeekerId());
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/{matchRequestId}/decline")
	MatchRequestSnapshot declineMatchRequest(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody JobSeekerMatchActionRequest request
	) {
		MatchRequestSnapshot snapshot = matchingService.declineMatchRequest(matchRequestId, request.jobSeekerId());
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/{matchRequestId}/owner-accept")
	MatchRequestSnapshot acceptMatchRequestByOwner(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody OwnerMatchActionRequest request
	) {
		MatchRequestSnapshot snapshot = matchingService.acceptMatchRequestByOwner(matchRequestId, request.ownerId());
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/{matchRequestId}/owner-decline")
	MatchRequestSnapshot declineMatchRequestByOwner(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody OwnerMatchActionRequest request
	) {
		MatchRequestSnapshot snapshot = matchingService.declineMatchRequestByOwner(matchRequestId, request.ownerId());
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/match-requests/{matchRequestId}/cancel")
	MatchRequestSnapshot cancelMatchRequest(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody OwnerMatchActionRequest request
	) {
		MatchRequestSnapshot snapshot = matchingService.cancelMatchRequest(matchRequestId, request.ownerId());
		liveUpdateService.publish("match-requests");
		return snapshot;
	}

	@PostMapping("/reviews")
	@ResponseStatus(HttpStatus.CREATED)
	Review createReview(@Valid @RequestBody CreateReviewRequest request) {
		Review review = matchingService.createReview(
			request.matchRequestId(),
			request.evaluatorId(),
			request.targetId(),
			request.rating(),
			request.review()
		);
		liveUpdateService.publish("reviews");
		return review;
	}

	record CreateRecruitmentRequest(
		@NotNull UUID ownerId,
		@NotBlank String title,
		String workTime,
		LocalDate workDate,
		LocalTime startTime,
		LocalTime endTime,
		@NotBlank String workplaceAddress,
		@Positive int hourlyWage
	) {
	}

	record CreateMatchRequestRequest(
		@NotNull UUID ownerId,
		@NotNull UUID jobSeekerId,
		UUID recruitmentId,
		@NotBlank String message
	) {
	}

	record CreateJobSeekerMatchRequest(
		@NotNull UUID jobSeekerId,
		@NotNull UUID ownerId,
		@NotBlank String message
	) {
	}

	record JobSeekerMatchActionRequest(
		@NotNull UUID jobSeekerId
	) {
	}

	record OwnerMatchActionRequest(
		@NotNull UUID ownerId
	) {
	}

	record CreateReviewRequest(
		@NotNull UUID matchRequestId,
		@NotNull UUID evaluatorId,
		@NotNull UUID targetId,
		@Min(1) @Max(5) int rating,
		@NotBlank String review
	) {
	}
}
