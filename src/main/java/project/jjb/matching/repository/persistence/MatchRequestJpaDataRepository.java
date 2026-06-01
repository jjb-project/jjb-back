package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MatchRequestJpaDataRepository extends JpaRepository<MatchRequestJpaEntity, UUID> {

	List<MatchRequestJpaEntity> findByJobSeekerIdOrderByStatusAsc(UUID jobSeekerId);

	List<MatchRequestJpaEntity> findByOwnerIdOrderByStatusAsc(UUID ownerId);
}
