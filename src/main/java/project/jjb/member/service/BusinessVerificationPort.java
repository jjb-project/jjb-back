package project.jjb.member.service;

import project.jjb.member.domain.BusinessVerificationCommand;
import project.jjb.member.domain.BusinessVerificationResult;

public interface BusinessVerificationPort {

	BusinessVerificationResult verify(BusinessVerificationCommand command);
}
