package project.jjb.member.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import project.jjb.member.domain.Member;
import project.jjb.member.domain.SocialIdentity;

public interface MemberRepository {

	Member save(Member member);

	Optional<Member> findById(UUID id);

	Optional<Member> findBySocialIdentity(SocialIdentity identity);

	List<Member> findJobSeekersWithProfiles();

	List<Member> findVerifiedOwnersWithProfiles();
}
