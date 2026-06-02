package project.jjb.matching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record Recruitment(
	UUID id,
	UUID ownerId,
	String title,
	LocalDate workDate,
	LocalTime startTime,
	LocalTime endTime,
	String workplaceAddress,
	int hourlyWage,
	RecruitmentStatus status,
	Instant createdAt
) {

	public String workTime() {
		return workDate + " " + startTime + "-" + endTime;
	}

	public boolean isOpen() {
		return status == RecruitmentStatus.OPEN;
	}
}
