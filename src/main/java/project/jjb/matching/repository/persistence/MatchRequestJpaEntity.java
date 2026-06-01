package project.jjb.matching.repository.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.matching.domain.MatchRequest;
import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.MatchRequestStatus;

@Entity
@Table(name = "match_requests")
class MatchRequestJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID ownerId;

	@Column(nullable = false)
	private UUID jobSeekerId;

	private UUID recruitmentId;

	@Column(nullable = false, length = 300)
	private String message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private MatchRequestStatus status;

	protected MatchRequestJpaEntity() {
	}

	static MatchRequestJpaEntity fromDomain(MatchRequest matchRequest) {
		MatchRequestSnapshot snapshot = matchRequest.snapshot();
		MatchRequestJpaEntity entity = new MatchRequestJpaEntity();
		entity.id = snapshot.id();
		entity.ownerId = snapshot.ownerId();
		entity.jobSeekerId = snapshot.jobSeekerId();
		entity.recruitmentId = snapshot.recruitmentId();
		entity.message = snapshot.message();
		entity.status = snapshot.status();
		return entity;
	}

	MatchRequest toDomain() {
		return MatchRequest.restore(id, ownerId, jobSeekerId, recruitmentId, message, status);
	}
}
