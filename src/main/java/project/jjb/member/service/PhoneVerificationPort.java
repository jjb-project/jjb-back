package project.jjb.member.service;

import project.jjb.member.domain.PhoneVerificationResult;

public interface PhoneVerificationPort {

	PhoneVerificationResult verify(String phoneNumber, String verificationCode);
}
