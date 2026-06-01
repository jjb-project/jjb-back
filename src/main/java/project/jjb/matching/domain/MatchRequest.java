package project.jjb.matching.domain;

import java.util.UUID;

import project.jjb.common.ApiException;

public class MatchRequest {

	private final UUID id;
	private final UUID ownerId;
	private final UUID jobSeekerId;
	private final UUID recruitmentId;
	private final String message;
	private MatchRequestStatus status = MatchRequestStatus.REQUESTED;

	public MatchRequest(UUID id, UUID ownerId, UUID jobSeekerId, UUID recruitmentId, String message) {
		this.id = id;
		this.ownerId = ownerId;
		this.jobSeekerId = jobSeekerId;
		this.recruitmentId = recruitmentId;
		this.message = message;
	}

	public static MatchRequest restore(
		UUID id,
		UUID ownerId,
		UUID jobSeekerId,
		UUID recruitmentId,
		String message,
		MatchRequestStatus status
	) {
		MatchRequest matchRequest = new MatchRequest(id, ownerId, jobSeekerId, recruitmentId, message);
		matchRequest.status = status;
		return matchRequest;
	}

	public void accept(UUID acceptingJobSeekerId) {
		if (!jobSeekerId.equals(acceptingJobSeekerId)) {
			throw ApiException.forbidden("MATCH_REQUEST_NOT_ASSIGNED", "Only the requested job seeker can accept this request.");
		}
		if (status != MatchRequestStatus.REQUESTED) {
			throw ApiException.conflict("MATCH_REQUEST_NOT_OPEN", "Only requested matches can be accepted.");
		}
		status = MatchRequestStatus.ACCEPTED;
	}

	public MatchRequestSnapshot snapshot() {
		return new MatchRequestSnapshot(id, ownerId, jobSeekerId, recruitmentId, message, status);
	}

	public UUID id() {
		return id;
	}
}
