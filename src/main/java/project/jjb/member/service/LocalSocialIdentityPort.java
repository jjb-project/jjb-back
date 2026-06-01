package project.jjb.member.service;

import org.springframework.stereotype.Component;
import project.jjb.common.ApiException;
import project.jjb.member.domain.SocialIdentity;

@Component
class LocalSocialIdentityPort implements SocialIdentityPort {

	@Override
	public SocialIdentity resolve(String provider, String subject) {
		if (isBlank(provider) || isBlank(subject)) {
			throw ApiException.badRequest("INVALID_SOCIAL_IDENTITY", "Social provider and subject are required.");
		}
		return new SocialIdentity(provider.trim().toUpperCase(), subject.trim());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
