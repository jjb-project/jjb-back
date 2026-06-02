package project.jjb.web;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveUpdateService {

	private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

	public SseEmitter subscribe() {
		SseEmitter emitter = new SseEmitter(0L);
		emitters.add(emitter);
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError(error -> emitters.remove(emitter));
		send(emitter, "connected");
		return emitter;
	}

	public void publish(String topic) {
		for (SseEmitter emitter : emitters) {
			send(emitter, topic);
		}
	}

	private void send(SseEmitter emitter, String topic) {
		try {
			emitter.send(SseEmitter.event()
				.name("jjb-update")
				.data(topic));
		}
		catch (IOException | IllegalStateException ignored) {
			emitters.remove(emitter);
		}
	}
}
