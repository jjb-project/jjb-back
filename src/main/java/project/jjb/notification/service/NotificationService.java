package project.jjb.notification.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import project.jjb.common.ApiException;
import project.jjb.notification.domain.Notification;
import project.jjb.notification.domain.NotificationType;
import project.jjb.notification.repository.NotificationRepository;
import project.jjb.web.LiveUpdateService;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final LiveUpdateService liveUpdateService;

	public NotificationService(NotificationRepository notificationRepository, LiveUpdateService liveUpdateService) {
		this.notificationRepository = notificationRepository;
		this.liveUpdateService = liveUpdateService;
	}

	@Transactional
	public Notification notify(UUID recipientId, NotificationType type, String title, String body, String linkUrl) {
		Notification saved = notificationRepository.save(Notification.create(recipientId, type, title, body, linkUrl));
		liveUpdateService.publish("notifications");
		return saved;
	}

	@Transactional(readOnly = true)
	public List<Notification> list(UUID recipientId) {
		return notificationRepository.findByRecipientId(recipientId);
	}

	@Transactional(readOnly = true)
	public long unreadCount(UUID recipientId) {
		return notificationRepository.countUnread(recipientId);
	}

	@Transactional
	public Notification markRead(UUID notificationId, UUID recipientId) {
		Notification notification = notificationRepository.findById(notificationId)
			.orElseThrow(() -> ApiException.notFound("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다."));
		if (!notification.recipientId().equals(recipientId)) {
			throw ApiException.forbidden("NOTIFICATION_NOT_OWNED", "본인의 알림만 읽을 수 있습니다.");
		}
		if (notification.read()) {
			return notification;
		}
		return notificationRepository.save(notification.markRead());
	}

	@Transactional
	public void markAllRead(UUID recipientId) {
		notificationRepository.markAllRead(recipientId);
		liveUpdateService.publish("notifications");
	}
}
