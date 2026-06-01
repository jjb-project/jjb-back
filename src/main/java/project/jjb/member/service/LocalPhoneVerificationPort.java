package project.jjb.member.service;

import org.springframework.stereotype.Component;
import project.jjb.member.domain.PhoneVerificationResult;

@Component
class LocalPhoneVerificationPort implements PhoneVerificationPort {

	@Override
	public PhoneVerificationResult verify(String phoneNumber, String verificationCode) {
		boolean verified = !isBlank(phoneNumber) && !isBlank(verificationCode) && verificationCode.trim().length() >= 4;
		String normalized = phoneNumber == null ? null : phoneNumber.replaceAll("[^0-9]", "");
		return new PhoneVerificationResult(verified, normalized);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
