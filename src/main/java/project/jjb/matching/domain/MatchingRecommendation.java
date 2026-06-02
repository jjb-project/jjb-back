package project.jjb.matching.domain;

import java.util.UUID;

public record MatchingRecommendation(
	UUID jobSeekerId,
	String displayName,
	int score,
	String reason,
	String profileSummary
) {
}
