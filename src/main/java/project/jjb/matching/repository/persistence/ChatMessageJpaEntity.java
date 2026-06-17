package project.jjb.matching.repository.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import project.jjb.matching.domain.ChatMessage;

@Entity
@Table(name = "chat_messages")
class ChatMessageJpaEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID matchRequestId;

	@Column(nullable = false)
	private UUID senderId;

	@Column(nullable = false, length = 1000)
	private String body;

	@Column(nullable = false)
	private Instant createdAt;

	protected ChatMessageJpaEntity() {
	}

	static ChatMessageJpaEntity fromDomain(ChatMessage message) {
		ChatMessageJpaEntity entity = new ChatMessageJpaEntity();
		entity.id = message.id();
		entity.matchRequestId = message.matchRequestId();
		entity.senderId = message.senderId();
		entity.body = message.body();
		entity.createdAt = message.createdAt();
		return entity;
	}

	ChatMessage toDomain() {
		return new ChatMessage(id, matchRequestId, senderId, body, createdAt);
	}
}
