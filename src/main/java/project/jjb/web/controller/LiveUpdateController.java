package project.jjb.web.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import project.jjb.web.LiveUpdateService;

@RestController
class LiveUpdateController {

	private final LiveUpdateService liveUpdateService;

	LiveUpdateController(LiveUpdateService liveUpdateService) {
		this.liveUpdateService = liveUpdateService;
	}

	@GetMapping(path = "/live/updates", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	SseEmitter updates() {
		return liveUpdateService.subscribe();
	}
}
