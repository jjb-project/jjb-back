package project.jjb.matching.repository.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import project.jjb.matching.domain.Favorite;
import project.jjb.matching.domain.FavoriteTargetType;

@Entity
@Table(
	name = "favorites",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_favorites_member_target",
		columnNames = {"member_id", "target_id", "target_type"}
	)
)
class FavoriteJpaEntity {

	@Id
	private UUID id;

	private UUID memberId;

	private UUID targetId;

	@Enumerated(EnumType.STRING)
	private FavoriteTargetType targetType;

	private Instant createdAt;

	protected FavoriteJpaEntity() {
	}

	static FavoriteJpaEntity fromDomain(Favorite favorite) {
		FavoriteJpaEntity entity = new FavoriteJpaEntity();
		entity.id = favorite.id();
		entity.memberId = favorite.memberId();
		entity.targetId = favorite.targetId();
		entity.targetType = favorite.targetType();
		entity.createdAt = favorite.createdAt();
		return entity;
	}

	Favorite toDomain() {
		return new Favorite(id, memberId, targetId, targetType, createdAt);
	}
}
