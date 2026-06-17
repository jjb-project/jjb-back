package project.jjb.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import project.jjb.notification.domain.Notification;

public interface NotificationRepository {

	Notification save(Notification notification);

	Optional<Notification> findById(UUID id);

	List<Notification> findByRecipientId(UUID recipientId);

	long countUnread(UUID recipientId);

	void markAllRead(UUID recipientId);
}
