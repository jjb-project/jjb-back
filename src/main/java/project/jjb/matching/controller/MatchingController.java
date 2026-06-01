package project.jjb.matching.controller;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.service.MatchingService;

@RestController
@RequestMapping("/api")
public class MatchingController {

	private final MatchingService matchingService;

	public MatchingController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	@PostMapping("/recruitments")
	@ResponseStatus(HttpStatus.CREATED)
	Recruitment createRecruitment(@Valid @RequestBody CreateRecruitmentRequest request) {
		return matchingService.createRecruitment(
			request.ownerId(),
			request.title(),
			request.workTime(),
			request.workplaceAddress(),
			request.hourlyWage()
		);
	}

	@PostMapping("/match-requests")
	@ResponseStatus(HttpStatus.CREATED)
	MatchRequestSnapshot createMatchRequest(@Valid @RequestBody CreateMatchRequestRequest request) {
		return matchingService.createMatchRequest(
			request.ownerId(),
			request.jobSeekerId(),
			request.recruitmentId(),
			request.message()
		);
	}

	@PostMapping("/match-requests/{matchRequestId}/accept")
	MatchRequestSnapshot acceptMatchRequest(
		@PathVariable UUID matchRequestId,
		@Valid @RequestBody AcceptMatchRequest request
	) {
		return matchingService.acceptMatchRequest(matchRequestId, request.jobSeekerId());
	}

	record CreateRecruitmentRequest(
		@NotNull UUID ownerId,
		@NotBlank String title,
		@NotBlank String workTime,
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

	record AcceptMatchRequest(
		@NotNull UUID jobSeekerId
	) {
	}
}
