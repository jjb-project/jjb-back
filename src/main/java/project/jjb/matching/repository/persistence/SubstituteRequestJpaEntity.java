package project.jjb.matching.repository.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.matching.domain.SubstituteRequest;
import project.jjb.matching.domain.SubstituteStatus;

@Entity
@Table(name = "substitute_requests")
class SubstituteRequestJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID requesterId;

	@Column(nullable = false)
	private UUID ownerId;

	private UUID recruitmentId;

	@Column(length = 120)
	private String storeName;

	@Column(length = 160)
	private String shiftInfo;

	@Column(length = 300)
	private String reason;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SubstituteStatus status;

	private UUID filledById;

	@Column(nullable = false)
	private Instant createdAt;

	protected SubstituteRequestJpaEntity() {
	}

	static SubstituteRequestJpaEntity fromDomain(SubstituteRequest request) {
		SubstituteRequestJpaEntity entity = new SubstituteRequestJpaEntity();
		entity.id = request.id();
		entity.requesterId = request.requesterId();
		entity.ownerId = request.ownerId();
		entity.recruitmentId = request.recruitmentId();
		entity.storeName = request.storeName();
		entity.shiftInfo = request.shiftInfo();
		entity.reason = request.reason();
		entity.status = request.status();
		entity.filledById = request.filledById();
		entity.createdAt = request.createdAt();
		return entity;
	}

	SubstituteRequest toDomain() {
		return new SubstituteRequest(id, requesterId, ownerId, recruitmentId, storeName, shiftInfo, reason,
			status, filledById, createdAt);
	}
}
