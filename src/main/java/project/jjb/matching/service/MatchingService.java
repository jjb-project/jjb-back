package project.jjb.matching.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.common.ApiException;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.member.service.MemberService;

@Service
public class MatchingService {

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
		String workTime,
		String workplaceAddress,
		int hourlyWage
	) {
		memberService.requireOwnerReady(ownerId);
		Recruitment recruitment = new Recruitment(UUID.randomUUID(), ownerId, title, workTime, workplaceAddress, hourlyWage);
		return matchingRepository.saveRecruitment(recruitment);
	}

	@Transactional(readOnly = true)
	public List<Recruitment> listRecruitments() {
		return matchingRepository.findRecruitments();
	}

	@Transactional(readOnly = true)
	public Recruitment getRecruitment(UUID recruitmentId) {
		return matchingRepository.findRecruitmentById(recruitmentId)
			.orElseThrow(() -> ApiException.notFound("RECRUITMENT_NOT_FOUND", "Recruitment was not found."));
	}

	@Transactional
	public MatchRequestSnapshot createMatchRequest(UUID ownerId, UUID jobSeekerId, UUID recruitmentId, String message) {
		memberService.requireOwnerReady(ownerId);
		memberService.requireJobSeekerReady(jobSeekerId);
		MatchRequest matchRequest = new MatchRequest(UUID.randomUUID(), ownerId, jobSeekerId, recruitmentId, message);
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

	@Transactional
	public MatchRequestSnapshot acceptMatchRequest(UUID matchRequestId, UUID jobSeekerId) {
		MatchRequest matchRequest = matchingRepository.findMatchRequestById(matchRequestId)
			.orElseThrow(() -> ApiException.notFound("MATCH_REQUEST_NOT_FOUND", "Match request was not found."));
		matchRequest.accept(jobSeekerId);
		return matchingRepository.saveMatchRequest(matchRequest).snapshot();
	}
}
