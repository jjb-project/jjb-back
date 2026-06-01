package project.jjb.matching.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.Recruitment;

public interface MatchingRepository {

	Recruitment saveRecruitment(Recruitment recruitment);

	List<Recruitment> findRecruitments();

	Optional<Recruitment> findRecruitmentById(UUID id);

	MatchRequest saveMatchRequest(MatchRequest matchRequest);

	Optional<MatchRequest> findMatchRequestById(UUID id);

	List<MatchRequest> findMatchRequestsByJobSeekerId(UUID jobSeekerId);

	List<MatchRequest> findMatchRequestsByOwnerId(UUID ownerId);
}
