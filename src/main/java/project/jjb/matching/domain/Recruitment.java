package project.jjb.matching.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
	Instant createdAt,
	List<String> tags,
	String description,
	boolean instantHire
) {

	public Recruitment {
		tags = tags == null ? List.of() : List.copyOf(tags);
		description = description == null ? "" : description;
	}

	public String workTime() {
		return workDate + " " + startTime + "-" + endTime;
	}

	public boolean isOpen() {
		return status == RecruitmentStatus.OPEN;
	}

	public Recruitment close() {
		return new Recruitment(id, ownerId, title, workDate, startTime, endTime, workplaceAddress, hourlyWage,
			RecruitmentStatus.CLOSED, createdAt, tags, description, instantHire);
	}

	public Recruitment withDetails(
		String newTitle,
		LocalDate newWorkDate,
		LocalTime newStartTime,
		LocalTime newEndTime,
		String newWorkplaceAddress,
		int newHourlyWage,
		List<String> newTags,
		String newDescription,
		boolean newInstantHire
	) {
		return new Recruitment(id, ownerId, newTitle, newWorkDate, newStartTime, newEndTime, newWorkplaceAddress,
			newHourlyWage, status, createdAt, newTags, newDescription, newInstantHire);
	}
}
