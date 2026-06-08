package project.jjb.matching.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import project.jjb.matching.domain.Favorite;
import project.jjb.matching.domain.FavoriteTargetType;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.Review;
import project.jjb.matching.domain.Recruitment;

public interface MatchingRepository {

	Recruitment saveRecruitment(Recruitment recruitment);

	List<Recruitment> findRecruitments();

	List<Recruitment> findRecruitmentsByOwnerId(UUID ownerId);

	Optional<Recruitment> findRecruitmentById(UUID id);

	MatchRequest saveMatchRequest(MatchRequest matchRequest);

	Optional<MatchRequest> findMatchRequestById(UUID id);

	List<MatchRequest> findMatchRequestsByJobSeekerId(UUID jobSeekerId);

	List<MatchRequest> findMatchRequestsByOwnerId(UUID ownerId);

	List<MatchRequest> findMatchRequestsByParticipantId(UUID memberId);

	Review saveReview(Review review);

	boolean existsReviewByMatchRequestIdAndEvaluatorId(UUID matchRequestId, UUID evaluatorId);

	List<Review> findReviewsByTargetId(UUID targetId);

	List<Review> findReviewsByEvaluatorId(UUID evaluatorId);

	Favorite saveFavorite(Favorite favorite);

	void deleteFavorite(UUID memberId, UUID targetId, FavoriteTargetType targetType);

	boolean existsFavorite(UUID memberId, UUID targetId, FavoriteTargetType targetType);

	List<Favorite> findFavoritesByMemberIdAndTargetType(UUID memberId, FavoriteTargetType targetType);
}
