package project.jjb.member.domain;

public record SocialIdentity(
	String provider,
	String subject
) {
}
