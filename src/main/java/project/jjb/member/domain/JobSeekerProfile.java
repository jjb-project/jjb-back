package project.jjb.member.domain;

import java.util.List;

public record JobSeekerProfile(
	String availableTime,
	String preferredArea,
	int desiredHourlyWage,
	List<String> experiencedIndustries,
	boolean urgentSubstituteAvailable,
	String introduction
) {

	public JobSeekerProfile {
		experiencedIndustries = experiencedIndustries == null ? List.of() : List.copyOf(experiencedIndustries);
	}
}
