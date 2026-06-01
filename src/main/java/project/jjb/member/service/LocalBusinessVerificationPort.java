package project.jjb.member.service;

import org.springframework.stereotype.Component;
import project.jjb.member.domain.BusinessOperatingStatus;
import project.jjb.member.domain.BusinessVerificationCommand;
import project.jjb.member.domain.BusinessVerificationResult;

@Component
class LocalBusinessVerificationPort implements BusinessVerificationPort {

	@Override
	public BusinessVerificationResult verify(BusinessVerificationCommand command) {
		boolean verified = command != null
			&& !isBlank(command.businessRegistrationNumber())
			&& !isBlank(command.representativeName())
			&& command.openingDate() != null;
		return new BusinessVerificationResult(verified, verified ? BusinessOperatingStatus.OPERATING : null);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
