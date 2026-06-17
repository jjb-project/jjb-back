package project.jjb.notification.repository.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NotificationJpaDataRepository extends JpaRepository<NotificationJpaEntity, UUID> {

	List<NotificationJpaEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

	long countByRecipientIdAndReadFlagFalse(UUID recipientId);

	@Modifying
	@Query("update NotificationJpaEntity n set n.readFlag = true where n.recipientId = :recipientId and n.readFlag = false")
	void markAllRead(@Param("recipientId") UUID recipientId);
}
