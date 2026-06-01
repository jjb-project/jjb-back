package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.matching.domain.Recruitment;

@Repository
public class JpaMatchingRepository implements MatchingRepository {

	private final RecruitmentJpaDataRepository recruitmentJpaDataRepository;
	private final MatchRequestJpaDataRepository matchRequestJpaDataRepository;

	public JpaMatchingRepository(
		RecruitmentJpaDataRepository recruitmentJpaDataRepository,
		MatchRequestJpaDataRepository matchRequestJpaDataRepository
	) {
		this.recruitmentJpaDataRepository = recruitmentJpaDataRepository;
		this.matchRequestJpaDataRepository = matchRequestJpaDataRepository;
	}

	@Override
	public Recruitment saveRecruitment(Recruitment recruitment) {
		return recruitmentJpaDataRepository.save(RecruitmentJpaEntity.fromDomain(recruitment)).toDomain();
	}

	@Override
	public List<Recruitment> findRecruitments() {
		return recruitmentJpaDataRepository.findAllByOrderByTitleAsc().stream()
			.map(RecruitmentJpaEntity::toDomain)
			.toList();
	}

	@Override
	public Optional<Recruitment> findRecruitmentById(UUID id) {
		return recruitmentJpaDataRepository.findById(id)
			.map(RecruitmentJpaEntity::toDomain);
	}

	@Override
	public MatchRequest saveMatchRequest(MatchRequest matchRequest) {
		return matchRequestJpaDataRepository.save(MatchRequestJpaEntity.fromDomain(matchRequest)).toDomain();
	}

	@Override
	public Optional<MatchRequest> findMatchRequestById(UUID id) {
		return matchRequestJpaDataRepository.findById(id)
			.map(MatchRequestJpaEntity::toDomain);
	}

	@Override
	public List<MatchRequest> findMatchRequestsByJobSeekerId(UUID jobSeekerId) {
		return matchRequestJpaDataRepository.findByJobSeekerIdOrderByStatusAsc(jobSeekerId).stream()
			.map(MatchRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<MatchRequest> findMatchRequestsByOwnerId(UUID ownerId) {
		return matchRequestJpaDataRepository.findByOwnerIdOrderByStatusAsc(ownerId).stream()
			.map(MatchRequestJpaEntity::toDomain)
			.toList();
	}
}
