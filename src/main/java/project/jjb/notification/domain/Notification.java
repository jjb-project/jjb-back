package project.jjb.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record Notification(
	UUID id,
	UUID recipientId,
	NotificationType type,
	String title,
	String body,
	String linkUrl,
	boolean read,
	Instant createdAt
) {

	public static Notification create(
		UUID recipientId,
		NotificationType type,
		String title,
		String body,
		String linkUrl
	) {
		return new Notification(UUID.randomUUID(), recipientId, type, title, body, linkUrl, false, Instant.now());
	}

	public Notification markRead() {
		return new Notification(id, recipientId, type, title, body, linkUrl, true, createdAt);
	}
}
