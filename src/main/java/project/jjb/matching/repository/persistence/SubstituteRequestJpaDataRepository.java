package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import project.jjb.matching.domain.SubstituteStatus;

interface SubstituteRequestJpaDataRepository extends JpaRepository<SubstituteRequestJpaEntity, UUID> {

	List<SubstituteRequestJpaEntity> findByStatusOrderByCreatedAtDesc(SubstituteStatus status);

	List<SubstituteRequestJpaEntity> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

	List<SubstituteRequestJpaEntity> findByFilledByIdOrderByCreatedAtDesc(UUID filledById);
}
