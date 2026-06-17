package project.jjb.member.domain;

import java.util.List;

public record JobSeekerProfile(
	String availableTime,
	String preferredArea,
	int desiredHourlyWage,
	List<String> experiencedIndustries,
	boolean urgentSubstituteAvailable,
	String introduction,
	String gender,
	String militaryService,
	String education,
	String careerLevel,
	List<String> preferredDays,
	String imageUrl
) {

	public JobSeekerProfile {
		experiencedIndustries = experiencedIndustries == null ? List.of() : List.copyOf(experiencedIndustries);
		preferredDays = preferredDays == null ? List.of() : List.copyOf(preferredDays);
	}
}
