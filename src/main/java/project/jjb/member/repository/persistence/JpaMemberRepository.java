package project.jjb.member.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import project.jjb.member.domain.Member;
import project.jjb.member.domain.SocialIdentity;
import project.jjb.member.repository.MemberRepository;

@Repository
public class JpaMemberRepository implements MemberRepository {

	private final MemberJpaDataRepository memberJpaDataRepository;

	public JpaMemberRepository(MemberJpaDataRepository memberJpaDataRepository) {
		this.memberJpaDataRepository = memberJpaDataRepository;
	}

	@Override
	public Member save(Member member) {
		return memberJpaDataRepository.save(MemberJpaEntity.fromDomain(member)).toDomain();
	}

	@Override
	public Optional<Member> findById(UUID id) {
		return memberJpaDataRepository.findById(id)
			.map(MemberJpaEntity::toDomain);
	}

	@Override
	public Optional<Member> findBySocialIdentity(SocialIdentity identity) {
		return memberJpaDataRepository.findBySocialProviderAndSocialSubject(identity.provider(), identity.subject())
			.map(MemberJpaEntity::toDomain);
	}

	@Override
	public List<Member> findJobSeekersWithProfiles() {
		return memberJpaDataRepository.findByAvailableTimeIsNotNullOrderByDisplayNameAsc().stream()
			.map(MemberJpaEntity::toDomain)
			.toList();
	}

	@Override
	public List<Member> findVerifiedOwnersWithProfiles() {
		return memberJpaDataRepository.findByBusinessVerifiedTrueAndStoreNameIsNotNullOrderByStoreNameAsc().stream()
			.map(MemberJpaEntity::toDomain)
			.toList();
	}
}
