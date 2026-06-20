package project.jjb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import project.jjb.matching.domain.SubstituteRequest;
import project.jjb.matching.domain.SubstituteStatus;
import project.jjb.matching.repository.MatchingRepository;
import project.jjb.matching.service.MatchingService;

@ActiveProfiles("test")
@SpringBootTest
class MatchHistoryServiceTests {

	@Autowired
	MatchingService matchingService;

	@Autowired
	MatchingRepository matchingRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanup() {
		jdbcTemplate.update("delete from substitute_requests where store_name like 'MHT-%'");
	}

	@Test
	void listSubstituteRequestsByFillerReturnsOnlyFilledTakenByMember() {
		UUID requester = UUID.randomUUID();
		UUID owner = UUID.randomUUID();
		UUID taker = UUID.randomUUID();

		SubstituteRequest filled = SubstituteRequest.open(requester, owner, null,
			"MHT-카페 A", "오늘 18:00-22:00", "병원 예약");
		matchingRepository.saveSubstituteRequest(filled.fill(taker));

		// 다른 사람이 맡은 건은 잡히면 안 된다.
		SubstituteRequest filledByOther = SubstituteRequest.open(requester, owner, null,
			"MHT-카페 B", "내일 10:00-14:00", "개인 사정");
		matchingRepository.saveSubstituteRequest(filledByOther.fill(UUID.randomUUID()));

		List<SubstituteRequest> result = matchingService.listSubstituteRequestsByFiller(taker);

		assertThat(result).extracting(SubstituteRequest::id).containsExactly(filled.id());
		assertThat(result).allMatch(r -> r.status() == SubstituteStatus.FILLED);
		assertThat(result).allMatch(r -> taker.equals(r.filledById()));
	}
}
