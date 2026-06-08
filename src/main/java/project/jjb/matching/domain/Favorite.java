package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

public record Favorite(
	UUID id,
	UUID memberId,
	UUID targetId,
	FavoriteTargetType targetType,
	Instant createdAt
) {
}
