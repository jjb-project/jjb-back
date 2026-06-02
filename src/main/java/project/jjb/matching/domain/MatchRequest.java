package project.jjb.matching.domain;

import java.time.Instant;
import java.util.UUID;

import project.jjb.common.ApiException;

public class MatchRequest {

	private final UUID id;
	private final UUID ownerId;
	private final UUID jobSeekerId;
	private final UUID recruitmentId;
	private final String message;
	private final MatchRequestInitiator requestedBy;
	private final Instant createdAt;
	private MatchRequestStatus status = MatchRequestStatus.REQUESTED;
	private Instant respondedAt;

	public MatchRequest(UUID id, UUID ownerId, UUID jobSeekerId, UUID recruitmentId, String message) {
		this(id, ownerId, jobSeekerId, recruitmentId, message, MatchRequestInitiator.OWNER, Instant.now());
	}

	public MatchRequest(
		UUID id,
		UUID ownerId,
		UUID jobSeekerId,
		UUID recruitmentId,
		String message,
		MatchRequestInitiator requestedBy
	) {
		this(id, ownerId, jobSeekerId, recruitmentId, message, requestedBy, Instant.now());
	}

	public MatchRequest(
		UUID id,
		UUID ownerId,
		UUID jobSeekerId,
		UUID recruitmentId,
		String message,
		MatchRequestInitiator requestedBy,
		Instant createdAt
	) {
		this.id = id;
		this.ownerId = ownerId;
		this.jobSeekerId = jobSeekerId;
		this.recruitmentId = recruitmentId;
		this.message = message;
		this.requestedBy = requestedBy;
		this.createdAt = createdAt;
	}

	public static MatchRequest restore(
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
		MatchRequest matchRequest = new MatchRequest(id, ownerId, jobSeekerId, recruitmentId, message, requestedBy, createdAt);
		matchRequest.status = status;
		matchRequest.respondedAt = respondedAt;
		return matchRequest;
	}

	public void accept(UUID acceptingJobSeekerId) {
		if (requestedBy != MatchRequestInitiator.OWNER) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested owner can accept this request.");
		}
		if (!jobSeekerId.equals(acceptingJobSeekerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested job seeker can accept this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be accepted.");
		}
		status = MatchRequestStatus.ACCEPTED;
		respondedAt = Instant.now();
	}

	public void decline(UUID decliningJobSeekerId) {
		if (requestedBy != MatchRequestInitiator.OWNER) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested owner can decline this request.");
		}
		if (!jobSeekerId.equals(decliningJobSeekerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested job seeker can decline this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be declined.");
		}
		status = MatchRequestStatus.DECLINED;
		respondedAt = Instant.now();
	}

	public void acceptByOwner(UUID acceptingOwnerId) {
		if (requestedBy != MatchRequestInitiator.JOB_SEEKER) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested job seeker can accept this request.");
		}
		if (!ownerId.equals(acceptingOwnerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_OWNED", "Only the requested owner can accept this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be accepted.");
		}
		status = MatchRequestStatus.ACCEPTED;
		respondedAt = Instant.now();
	}

	public void declineByOwner(UUID decliningOwnerId) {
		if (requestedBy != MatchRequestInitiator.JOB_SEEKER) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested job seeker can decline this request.");
		}
		if (!ownerId.equals(decliningOwnerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_OWNED", "Only the requested owner can decline this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be declined.");
		}
		status = MatchRequestStatus.DECLINED;
		respondedAt = Instant.now();
	}

	public void cancelByJobSeeker(UUID cancelingJobSeekerId) {
		if (requestedBy != MatchRequestInitiator.JOB_SEEKER) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requesting owner can cancel this request.");
		}
		if (!jobSeekerId.equals(cancelingJobSeekerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requesting job seeker can cancel this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be canceled.");
		}
		status = MatchRequestStatus.CANCELED;
		respondedAt = Instant.now();
	}

	public void cancel(UUID cancelingOwnerId) {
		if (!ownerId.equals(cancelingOwnerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_OWNED", "Only the requesting owner can cancel this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be canceled.");
		}
		status = MatchRequestStatus.CANCELED;
		respondedAt = Instant.now();
	}

	public MatchRequestSnapshot snapshot() {
		return new MatchRequestSnapshot(id, ownerId, jobSeekerId, recruitmentId, message, status, requestedBy, createdAt, respondedAt);
	}

	public UUID id() {
		return id;
	}

	public UUID ownerId() {
		return ownerId;
	}

	public UUID jobSeekerId() {
		return jobSeekerId;
	}

	public UUID recruitmentId() {
		return recruitmentId;
	}

	public MatchRequestInitiator requestedBy() {
		return requestedBy;
	}

	public MatchRequestStatus status() {
		return status;
	}
}
