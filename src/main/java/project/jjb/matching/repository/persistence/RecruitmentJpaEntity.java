package project.jjb.matching.repository.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.matching.domain.Recruitment;

@Entity
@Table(name = "recruitments")
class RecruitmentJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID ownerId;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(nullable = false, length = 120)
	private String workTime;

	@Column(nullable = false, length = 240)
	private String workplaceAddress;

	@Column(nullable = false)
	private int hourlyWage;

	protected RecruitmentJpaEntity() {
	}

	static RecruitmentJpaEntity fromDomain(Recruitment recruitment) {
		RecruitmentJpaEntity entity = new RecruitmentJpaEntity();
		entity.id = recruitment.id();
		entity.ownerId = recruitment.ownerId();
		entity.title = recruitment.title();
		entity.workTime = recruitment.workTime();
		entity.workplaceAddress = recruitment.workplaceAddress();
		entity.hourlyWage = recruitment.hourlyWage();
		return entity;
	}

	Recruitment toDomain() {
		return new Recruitment(id, ownerId, title, workTime, workplaceAddress, hourlyWage);
	}
}
