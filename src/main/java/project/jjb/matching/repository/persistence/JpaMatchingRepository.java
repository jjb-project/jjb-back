package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.matching.domain.Favorite;
import project.jjb.matching.domain.FavoriteTargetType;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.ChatMessage;
import project.jjb.matching.domain.Review;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.SubstituteRequest;
import project.jjb.matching.domain.SubstituteStatus;

@Repository
public class JpaMatchingRepository implements MatchingRepository {

	private final RecruitmentJpaDataRepository recruitmentJpaDataRepository;
	private final MatchRequestJpaDataRepository matchRequestJpaDataRepository;
	private final ReviewJpaDataRepository reviewJpaDataRepository;
	private final FavoriteJpaDataRepository favoriteJpaDataRepository;
	private final SubstituteRequestJpaDataRepository substituteRequestJpaDataRepository;
	private final ChatMessageJpaDataRepository chatMessageJpaDataRepository;

	public JpaMatchingRepository(
		RecruitmentJpaDataRepository recruitmentJpaDataRepository,
		MatchRequestJpaDataRepository matchRequestJpaDataRepository,
		ReviewJpaDataRepository reviewJpaDataRepository,
		FavoriteJpaDataRepository favoriteJpaDataRepository,
		SubstituteRequestJpaDataRepository substituteRequestJpaDataRepository,
		ChatMessageJpaDataRepository chatMessageJpaDataRepository
	) {
		this.recruitmentJpaDataRepository = recruitmentJpaDataRepository;
		this.matchRequestJpaDataRepository = matchRequestJpaDataRepository;
		this.reviewJpaDataRepository = reviewJpaDataRepository;
		this.favoriteJpaDataRepository = favoriteJpaDataRepository;
		this.substituteRequestJpaDataRepository = substituteRequestJpaDataRepository;
		this.chatMessageJpaDataRepository = chatMessageJpaDataRepository;
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
	@Transactional
	public void deleteRecruitment(UUID id) {
		recruitmentJpaDataRepository.deleteById(id);
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

	@Override
	public SubstituteRequest saveSubstituteRequest(SubstituteRequest request) {
		return substituteRequestJpaDataRepository.save(SubstituteRequestJpaEntity.fromDomain(request)).toDomain();
	}

	@Override
	public Optional<SubstituteRequest> findSubstituteRequestById(UUID id) {
		return substituteRequestJpaDataRepository.findById(id).map(SubstituteRequestJpaEntity::toDomain);
	}

	@Override
	public List<SubstituteRequest> findOpenSubstituteRequests() {
		return substituteRequestJpaDataRepository.findByStatusOrderByCreatedAtDesc(SubstituteStatus.OPEN).stream()
			.map(SubstituteRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<SubstituteRequest> findSubstituteRequestsByRequesterId(UUID requesterId) {
		return substituteRequestJpaDataRepository.findByRequesterIdOrderByCreatedAtDesc(requesterId).stream()
			.map(SubstituteRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<SubstituteRequest> findSubstituteRequestsByFilledById(UUID filledById) {
		return substituteRequestJpaDataRepository.findByFilledByIdOrderByCreatedAtDesc(filledById).stream()
			.map(SubstituteRequestJpaEntity::toDomain)
			.toList();
	}

	@Override
	public ChatMessage saveChatMessage(ChatMessage message) {
		return chatMessageJpaDataRepository.save(ChatMessageJpaEntity.fromDomain(message)).toDomain();
	}

	@Override
	public List<ChatMessage> findChatMessages(UUID matchRequestId) {
		return chatMessageJpaDataRepository.findByMatchRequestIdOrderByCreatedAtAsc(matchRequestId).stream()
			.map(ChatMessageJpaEntity::toDomain)
			.toList();
	}
}
