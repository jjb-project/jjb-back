package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

public record MatchRequestSnapshot(
	UUID id,
	UUID ownerId,
	UUID jobSeekerId,
	UUID recruitmentId,
	String message,
	MatchRequestStatus status,
	MatchRequestInitiator requestedBy,
	Instant createdAt,
	Instant respondedAt
) {
}
