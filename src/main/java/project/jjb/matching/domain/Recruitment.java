package project.jjb.matching.domain;

import java.util.UUID;

public record Recruitment(
	UUID id,
	UUID ownerId,
	String title,
	String workTime,
	String workplaceAddress,
	int hourlyWage
) {
}
