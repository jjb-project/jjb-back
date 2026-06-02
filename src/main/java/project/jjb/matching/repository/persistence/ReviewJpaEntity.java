package project.jjb.matching.repository.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import project.jjb.matching.domain.Review;

@Entity
@Table(
	name = "reviews",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_reviews_match_evaluator",
		columnNames = {"match_request_id", "evaluator_id"}
	)
)
class ReviewJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID matchRequestId;

	@Column(nullable = false)
	private UUID evaluatorId;

	@Column(nullable = false)
	private UUID targetId;

	@Column(nullable = false)
	private int rating;

	@Column(nullable = false, length = 500)
	private String review;

	@Column(nullable = false)
	private Instant createdAt;

	protected ReviewJpaEntity() {
	}

	static ReviewJpaEntity fromDomain(Review review) {
		ReviewJpaEntity entity = new ReviewJpaEntity();
		entity.id = review.id();
		entity.matchRequestId = review.matchRequestId();
		entity.evaluatorId = review.evaluatorId();
		entity.targetId = review.targetId();
		entity.rating = review.rating();
		entity.review = review.review();
		entity.createdAt = review.createdAt();
		return entity;
	}

	Review toDomain() {
		return new Review(id, matchRequestId, evaluatorId, targetId, rating, review, createdAt);
	}
}
