package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface RecruitmentJpaDataRepository extends JpaRepository<RecruitmentJpaEntity, UUID> {

	List<RecruitmentJpaEntity> findAllByOrderByTitleAsc();

	List<RecruitmentJpaEntity> findByOwnerIdOrderByTitleAsc(UUID ownerId);
}
