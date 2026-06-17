package project.jjb.ai;

import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Gemini 기반 글쓰기 도우미. 모델 호출이 실패하면 규칙 기반 문구로 폴백한다.
 */
@Service
public class AiWritingService {

	private final ObjectProvider<ChatModel> chatModelProvider;

	public AiWritingService(ObjectProvider<ChatModel> chatModelProvider) {
		this.chatModelProvider = chatModelProvider;
	}

	public String writeIntroduction(String gender, String careerLevel, String industries, String area, String availableTime) {
		String context = """
			역할: 단기 알바 구직자의 이력서 자기소개 문장 작성기
			성별: %s
			경력: %s
			경험 업종: %s
			희망 지역: %s
			가능 시간: %s
			""".formatted(
				blankToDash(gender), blankToDash(careerLevel), blankToDash(industries),
				blankToDash(area), blankToDash(availableTime));
		String system = "너는 한국 단기 알바 플랫폼의 자기소개 작성 도우미다. "
			+ "주어진 정보만으로 성실하고 신뢰감 있는 자기소개를 2~3문장, 존댓말로 작성해라. "
			+ "이모지나 과장 없이 자연스럽게. 사실을 지어내지 마라.";
		return generate(system, context).orElseGet(() -> fallbackIntroduction(careerLevel, industries, availableTime));
	}

	public String writeRecruitment(String title, String category, String address, int hourlyWage, String tags) {
		String context = """
			역할: 단기 알바 공고 문구 작성기
			제목/업무: %s
			업종: %s
			근무지: %s
			시급: %d원
			테마 태그: %s
			""".formatted(
				blankToDash(title), blankToDash(category), blankToDash(address), hourlyWage, blankToDash(tags));
		String system = "너는 한국 단기 알바 플랫폼의 공고 문구 작성 도우미다. "
			+ "주어진 조건으로 지원하고 싶어지는 매력적인 공고 본문을 3~4문장, 존댓말로 작성해라. "
			+ "근무 조건과 분위기를 자연스럽게 녹이고, 과장·허위는 금지.";
		return generate(system, context).orElseGet(() -> fallbackRecruitment(title, address, hourlyWage));
	}

	private Optional<String> generate(String system, String userContext) {
		ChatModel chatModel = chatModelProvider.getIfAvailable();
		if (chatModel == null) {
			return Optional.empty();
		}
		try {
			String content = ChatClient.create(chatModel)
				.prompt()
				.system(system)
				.user(userContext)
				.call()
				.content();
			if (content == null || content.isBlank()) {
				return Optional.empty();
			}
			return Optional.of(content.trim());
		}
		catch (RuntimeException ignored) {
			return Optional.empty();
		}
	}

	private String fallbackIntroduction(String careerLevel, String industries, String availableTime) {
		StringBuilder sb = new StringBuilder();
		if (industries != null && !industries.isBlank()) {
			sb.append(industries).append(" 분야 경험이 있어 ");
		}
		sb.append("맡은 일은 책임감 있게 끝까지 해내겠습니다.");
		if (availableTime != null && !availableTime.isBlank()) {
			sb.append(" ").append(availableTime).append(" 근무 가능하며 성실하게 임하겠습니다.");
		}
		return sb.toString();
	}

	private String fallbackRecruitment(String title, String address, int hourlyWage) {
		return "%s 함께하실 분을 찾습니다. 근무지는 %s이며 시급은 %,d원입니다. 초보자도 친절히 알려드리니 편하게 지원해주세요."
			.formatted(blankToDash(title), blankToDash(address), hourlyWage);
	}

	private String blankToDash(String value) {
		return value == null || value.isBlank() ? "미입력" : value.trim();
	}
}
