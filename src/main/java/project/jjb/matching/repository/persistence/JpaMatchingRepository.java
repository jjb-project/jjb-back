package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.matching.domain.Favorite;
import project.jjb.matching.domain.FavoriteTargetType;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.Review;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.matching.domain.Recruitment;

@Repository
public class JpaMatchingRepository implements MatchingRepository {

	private final RecruitmentJpaDataRepository recruitmentJpaDataRepository;
	private final MatchRequestJpaDataRepository matchRequestJpaDataRepository;
	private final ReviewJpaDataRepository reviewJpaDataRepository;
	private final FavoriteJpaDataRepository favoriteJpaDataRepository;

	public JpaMatchingRepository(
		RecruitmentJpaDataRepository recruitmentJpaDataRepository,
		MatchRequestJpaDataRepository matchRequestJpaDataRepository,
		ReviewJpaDataRepository reviewJpaDataRepository,
		FavoriteJpaDataRepository favoriteJpaDataRepository
	) {
		this.recruitmentJpaDataRepository = recruitmentJpaDataRepository;
		this.matchRequestJpaDataRepository = matchRequestJpaDataRepository;
		this.reviewJpaDataRepository = reviewJpaDataRepository;
		this.favoriteJpaDataRepository = favoriteJpaDataRepository;
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

	@Override
	public List<Review> findReviewsByEvaluatorId(UUID evaluatorId) {
		return reviewJpaDataRepository.findByEvaluatorIdOrderByCreatedAtDesc(evaluatorId).stream()
			.map(ReviewJpaEntity::toDomain)
			.toList();
	}

	@Override
	public Favorite saveFavorite(Favorite favorite) {
		return favoriteJpaDataRepository.save(FavoriteJpaEntity.fromDomain(favorite)).toDomain();
	}

	@Override
	@Transactional
	public void deleteFavorite(UUID memberId, UUID targetId, FavoriteTargetType targetType) {
		favoriteJpaDataRepository.deleteByMemberIdAndTargetIdAndTargetType(memberId, targetId, targetType);
	}

	@Override
	public boolean existsFavorite(UUID memberId, UUID targetId, FavoriteTargetType targetType) {
		return favoriteJpaDataRepository.existsByMemberIdAndTargetIdAndTargetType(memberId, targetId, targetType);
	}

	@Override
	public List<Favorite> findFavoritesByMemberIdAndTargetType(UUID memberId, FavoriteTargetType targetType) {
		return favoriteJpaDataRepository.findByMemberIdAndTargetType(memberId, targetType).stream()
			.map(FavoriteJpaEntity::toDomain)
			.toList();
	}
}
