package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface MatchRequestJpaDataRepository extends JpaRepository<MatchRequestJpaEntity, UUID> {

	List<MatchRequestJpaEntity> findByJobSeekerIdOrderByStatusAscCreatedAtDesc(UUID jobSeekerId);

	List<MatchRequestJpaEntity> findByOwnerIdOrderByStatusAscCreatedAtDesc(UUID ownerId);

	@Query("""
		select request
		from MatchRequestJpaEntity request
		where request.ownerId = :memberId or request.jobSeekerId = :memberId
		order by request.status asc, request.createdAt desc
		""")
	List<MatchRequestJpaEntity> findByParticipantId(UUID memberId);
}
