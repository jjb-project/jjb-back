package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

public record SubstituteRequest(
	UUID id,
	UUID requesterId,
	UUID ownerId,
	UUID recruitmentId,
	String storeName,
	String shiftInfo,
	String reason,
	SubstituteStatus status,
	UUID filledById,
	Instant createdAt
) {

	public static SubstituteRequest open(
		UUID requesterId,
		UUID ownerId,
		UUID recruitmentId,
		String storeName,
		String shiftInfo,
		String reason
	) {
		return new SubstituteRequest(UUID.randomUUID(), requesterId, ownerId, recruitmentId,
			storeName, shiftInfo, reason, SubstituteStatus.OPEN, null, Instant.now());
	}

	public boolean isOpen() {
		return status == SubstituteStatus.OPEN;
	}

	public SubstituteRequest fill(UUID takerId) {
		return new SubstituteRequest(id, requesterId, ownerId, recruitmentId, storeName, shiftInfo, reason,
			SubstituteStatus.FILLED, takerId, createdAt);
	}

	public SubstituteRequest cancel() {
		return new SubstituteRequest(id, requesterId, ownerId, recruitmentId, storeName, shiftInfo, reason,
			SubstituteStatus.CANCELED, filledById, createdAt);
	}
}
