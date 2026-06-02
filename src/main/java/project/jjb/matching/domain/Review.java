package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

public record Review(
	UUID id,
	UUID matchRequestId,
	UUID evaluatorId,
	UUID targetId,
	int rating,
	String review,
	Instant createdAt
) {
}
