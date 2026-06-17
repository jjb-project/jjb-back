package project.jjb.ai;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiAssistController {

	private final AiWritingService aiWritingService;

	public AiAssistController(AiWritingService aiWritingService) {
		this.aiWritingService = aiWritingService;
	}

	@PostMapping("/introduction")
	Map<String, String> introduction(@RequestBody Map<String, String> body) {
		String text = aiWritingService.writeIntroduction(
			body.getOrDefault("gender", ""),
			body.getOrDefault("careerLevel", ""),
			body.getOrDefault("industries", ""),
			body.getOrDefault("area", ""),
			body.getOrDefault("availableTime", "")
		);
		return Map.of("text", text);
	}

	@PostMapping("/recruitment")
	Map<String, String> recruitment(@RequestBody Map<String, String> body) {
		int wage = parseWage(body.get("hourlyWage"));
		String text = aiWritingService.writeRecruitment(
			body.getOrDefault("title", ""),
			body.getOrDefault("category", ""),
			body.getOrDefault("address", ""),
			wage,
			body.getOrDefault("tags", "")
		);
		return Map.of("text", text);
	}

	private int parseWage(String raw) {
		if (raw == null || raw.isBlank()) {
			return 0;
		}
		try {
			return Integer.parseInt(raw.trim());
		}
		catch (NumberFormatException ignored) {
			return 0;
		}
	}
}
