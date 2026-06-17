package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ChatMessageJpaDataRepository extends JpaRepository<ChatMessageJpaEntity, UUID> {

	List<ChatMessageJpaEntity> findByMatchRequestIdOrderByCreatedAtAsc(UUID matchRequestId);
}
