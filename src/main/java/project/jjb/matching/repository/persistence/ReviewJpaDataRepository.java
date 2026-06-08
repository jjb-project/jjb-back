package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ReviewJpaDataRepository extends JpaRepository<ReviewJpaEntity, UUID> {

	boolean existsByMatchRequestIdAndEvaluatorId(UUID matchRequestId, UUID evaluatorId);

	List<ReviewJpaEntity> findByTargetIdOrderByCreatedAtDesc(UUID targetId);

	List<ReviewJpaEntity> findByEvaluatorIdOrderByCreatedAtDesc(UUID evaluatorId);
}
