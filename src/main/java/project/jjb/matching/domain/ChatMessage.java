package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
	UUID id,
	UUID matchRequestId,
	UUID senderId,
	String body,
	Instant createdAt
) {

	public static ChatMessage create(UUID matchRequestId, UUID senderId, String body) {
		return new ChatMessage(UUID.randomUUID(), matchRequestId, senderId, body, Instant.now());
	}
}
