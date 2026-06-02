package project.jjb.member.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface MemberJpaDataRepository extends JpaRepository<MemberJpaEntity, UUID> {

	Optional<MemberJpaEntity> findBySocialProviderAndSocialSubject(String socialProvider, String socialSubject);

	List<MemberJpaEntity> findByAvailableTimeIsNotNullOrderByDisplayNameAsc();

	List<MemberJpaEntity> findByBusinessVerifiedTrueAndStoreNameIsNotNullOrderByStoreNameAsc();
}
