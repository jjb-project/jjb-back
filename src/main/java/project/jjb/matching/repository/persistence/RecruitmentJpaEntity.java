package project.jjb.matching.repository.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.domain.RecruitmentStatus;

@Entity
@Table(name = "recruitments")
class RecruitmentJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false)
	private LocalDate workDate;

	@Column(nullable = false)
	private LocalTime startTime;

	@Column(nullable = false)
	private LocalTime endTime;

	@Column(nullable = false, length = 240)
	private String workplaceAddress;

	@Column(nullable = false)
	private int hourlyWage;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private RecruitmentStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(length = 200)
	private String tags;

	@Column(length = 1000)
	private String description;

	@Column(nullable = false)
	private boolean instantHire;

	protected RecruitmentJpaEntity() {
	}

	static RecruitmentJpaEntity fromDomain(Recruitment recruitment) {
		RecruitmentJpaEntity entity = new RecruitmentJpaEntity();
		entity.id = recruitment.id();
		entity.ownerId = recruitment.ownerId();
		entity.title = recruitment.title();
		entity.workDate = recruitment.workDate();
		entity.startTime = recruitment.startTime();
		entity.endTime = recruitment.endTime();
		entity.workplaceAddress = recruitment.workplaceAddress();
		entity.hourlyWage = recruitment.hourlyWage();
		entity.status = recruitment.status();
		entity.createdAt = recruitment.createdAt();
		entity.tags = recruitment.tags().isEmpty() ? null : String.join(",", recruitment.tags());
		entity.description = recruitment.description() == null || recruitment.description().isBlank()
			? null : recruitment.description();
		entity.instantHire = recruitment.instantHire();
		return entity;
	}

	Recruitment toDomain() {
		java.util.List<String> tagList = tags == null || tags.isBlank()
			? java.util.List.of()
			: java.util.Arrays.stream(tags.split(",")).filter(s -> !s.isBlank()).toList();
		return new Recruitment(id, ownerId, title, workDate, startTime, endTime, workplaceAddress, hourlyWage, status, createdAt, tagList, description, instantHire);
	}
}
