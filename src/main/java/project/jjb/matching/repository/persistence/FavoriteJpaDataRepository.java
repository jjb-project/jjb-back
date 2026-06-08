package project.jjb.matching.repository.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import project.jjb.matching.domain.FavoriteTargetType;

interface FavoriteJpaDataRepository extends JpaRepository<FavoriteJpaEntity, UUID> {

	Optional<FavoriteJpaEntity> findByMemberIdAndTargetIdAndTargetType(UUID memberId, UUID targetId, FavoriteTargetType targetType);

	List<FavoriteJpaEntity> findByMemberIdAndTargetType(UUID memberId, FavoriteTargetType targetType);

	boolean existsByMemberIdAndTargetIdAndTargetType(UUID memberId, UUID targetId, FavoriteTargetType targetType);

	void deleteByMemberIdAndTargetIdAndTargetType(UUID memberId, UUID targetId, FavoriteTargetType targetType);
}
