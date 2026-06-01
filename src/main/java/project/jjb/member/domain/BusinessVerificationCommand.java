package project.jjb.member.domain;

import java.time.LocalDate;

public record BusinessVerificationCommand(
	String businessRegistrationNumber,
	String representativeName,
	LocalDate openingDate
) {
}
