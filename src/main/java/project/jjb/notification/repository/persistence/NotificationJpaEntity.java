package project.jjb.notification.repository.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.notification.domain.Notification;
import project.jjb.notification.domain.NotificationType;

@Entity
@Table(name = "notifications")
class NotificationJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40)
	private NotificationType type;

	@Column(nullable = false, length = 160)
	private String title;

	@Column(length = 500)
	private String body;

	@Column(length = 300)
	private String linkUrl;

	@Column(nullable = false)
	private boolean readFlag;

	@Column(nullable = false)
	private Instant createdAt;

	protected NotificationJpaEntity() {
	}

	static NotificationJpaEntity fromDomain(Notification notification) {
		NotificationJpaEntity entity = new NotificationJpaEntity();
		entity.id = notification.id();
		entity.recipientId = notification.recipientId();
		entity.type = notification.type();
		entity.title = notification.title();
		entity.body = notification.body();
		entity.linkUrl = notification.linkUrl();
		entity.readFlag = notification.read();
		entity.createdAt = notification.createdAt();
		return entity;
	}

	Notification toDomain() {
		return new Notification(id, recipientId, type, title, body, linkUrl, readFlag, createdAt);
	}
}
