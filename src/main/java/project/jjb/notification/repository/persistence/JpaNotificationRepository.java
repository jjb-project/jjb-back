package project.jjb.notification.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.notification.domain.Notification;
import project.jjb.notification.repository.NotificationRepository;

@Repository
public class JpaNotificationRepository implements NotificationRepository {

	private final NotificationJpaDataRepository dataRepository;

	public JpaNotificationRepository(NotificationJpaDataRepository dataRepository) {
		this.dataRepository = dataRepository;
	}

	@Override
	public Notification save(Notification notification) {
		return dataRepository.save(NotificationJpaEntity.fromDomain(notification)).toDomain();
	}

	@Override
	public Optional<Notification> findById(UUID id) {
		return dataRepository.findById(id).map(NotificationJpaEntity::toDomain);
	}

	@Override
	public List<Notification> findByRecipientId(UUID recipientId) {
		return dataRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
			.map(NotificationJpaEntity::toDomain)
			.toList();
	}

	@Override
	public long countUnread(UUID recipientId) {
		return dataRepository.countByRecipientIdAndReadFlagFalse(recipientId);
	}

	@Override
	@Transactional
	public void markAllRead(UUID recipientId) {
		dataRepository.markAllRead(recipientId);
	}
}
