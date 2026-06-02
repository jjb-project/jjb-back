package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.Review;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.matching.domain.Recruitment;

@Repository
public class JpaMatchingRepository implements MatchingRepository {

	private final RecruitmentJpaDataRepository recruitmentJpaDataRepository;
	private final MatchRequestJpaDataRepository matchRequestJpaDataRepository;
	private final ReviewJpaDataRepository reviewJpaDataRepository;

	public JpaMatchingRepository(
		RecruitmentJpaDataRepository recruitmentJpaDataRepository,
		MatchRequestJpaDataRepository matchRequestJpaDataRepository,
		ReviewJpaDataRepository reviewJpaDataRepository
	) {
		this.recruitmentJpaDataRepository = recruitmentJpaDataRepository;
		this.matchRequestJpaDataRepository = matchRequestJpaDataRepository;
		this.reviewJpaDataRepository = reviewJpaDataRepository;
	}

	@Override
	public Recruitment saveRecruitment(Recruitment recruitment) {
		return recruitmentJpaDataRepository.save(RecruitmentJpaEntity.fromDomain(recruitment)).toDomain();
	}

	@Override
	public List<Recruitment> findRecruitments() {
		return recruitmentJpaDataRepository.findAllByOrderByCreatedAtDesc().stream()
			.map(RecruitmentJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Recruitment> findRecruitmentsByOwnerId(UUID ownerId) {
		return recruitmentJpaDataRepository.findByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
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
		return matchRequestJpaDataRepository.findByJobSeekerIdOrderByStatusAscCreatedAtDesc(jobSeekerId).stream()
			.map(MatchRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<MatchRequest> findMatchRequestsByOwnerId(UUID ownerId) {
		return matchRequestJpaDataRepository.findByOwnerIdOrderByStatusAscCreatedAtDesc(ownerId).stream()
			.map(MatchRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<MatchRequest> findMatchRequestsByParticipantId(UUID memberId) {
		return matchRequestJpaDataRepository.findByParticipantId(memberId).stream()
			.map(MatchRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public Review saveReview(Review review) {
		return reviewJpaDataRepository.save(ReviewJpaEntity.fromDomain(review)).toDomain();
	}

	@Override
	public boolean existsReviewByMatchRequestIdAndEvaluatorId(UUID matchRequestId, UUID evaluatorId) {
		return reviewJpaDataRepository.existsByMatchRequestIdAndEvaluatorId(matchRequestId, evaluatorId);
	}

	@Override
	public List<Review> findReviewsByTargetId(UUID targetId) {
		return reviewJpaDataRepository.findByTargetIdOrderByCreatedAtDesc(targetId).stream()
			.map(ReviewJpaEntity::toDomain)
			.toList();
	}
}
