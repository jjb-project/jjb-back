package project.jjb.matching.domain;

import java.util.UUID;

public record HomeRecommendation(
	UUID targetId,
	String targetName,
	String targetType,
	int score,
	String reason,
	String summary
) {
}
