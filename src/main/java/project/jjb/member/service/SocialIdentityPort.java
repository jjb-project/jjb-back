package project.jjb.member.service;

import project.jjb.member.domain.SocialIdentity;

public interface SocialIdentityPort {

	SocialIdentity resolve(String provider, String subject);
}
