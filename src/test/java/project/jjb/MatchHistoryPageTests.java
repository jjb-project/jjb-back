package project.jjb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import project.jjb.web.WebSessionKeys;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class MatchHistoryPageTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@AfterEach
	void cleanup() {
		jdbcTemplate.update("""
			delete from substitute_requests
			where requester_id in (select id from members where social_subject like 'mvc-%')
			   or filled_by_id in (select id from members where social_subject like 'mvc-%')
			   or owner_id in (select id from members where social_subject like 'mvc-%')
			""");
		jdbcTemplate.update("""
			delete from notifications
			where recipient_id in (select id from members where social_subject like 'mvc-%')
			""");
		jdbcTemplate.update("""
			delete from match_requests
			where owner_id in (select id from members where social_subject like 'mvc-%')
			   or job_seeker_id in (select id from members where social_subject like 'mvc-%')
			""");
		jdbcTemplate.update("delete from members where social_subject like 'mvc-%'");
	}

	private MockHttpSession jobSeeker(String name) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/start").session(session)
				.param("socialProvider", "kakao").param("displayName", name))
			.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/role").session(session).param("role", "JOB_SEEKER"))
			.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/worker/profile").session(session)
				.param("availableTime", "오늘 17:00-22:00")
				.param("preferredArea", "서울 강남구")
				.param("desiredHourlyWage", "13000")
				.param("experiencedIndustries", "고객응대")
				.param("introduction", name + " 프로필"))
			.andExpect(status().is3xxRedirection());
		return session;
	}

	private MockHttpSession owner(String name) throws Exception {
		MockHttpSession session = new MockHttpSession();
		mockMvc.perform(post("/start").session(session)
				.param("socialProvider", "naver").param("displayName", name))
			.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/role").session(session).param("role", "OWNER"))
			.andExpect(status().is3xxRedirection());
		return session;
	}

	@Test
	void matchHistoryShowsFilledSubstituteForBothSides() throws Exception {
		MockHttpSession ownerSession = owner("MH Owner");
		MockHttpSession requesterSession = jobSeeker("MH Requester");
		MockHttpSession takerSession = jobSeeker("MH Taker");

		UUID ownerId = (UUID) ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);
		UUID requesterId = (UUID) requesterSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);

		// 구직자가 대타 요청을 올린다.
		mockMvc.perform(post("/substitutes").session(requesterSession)
				.param("ownerId", ownerId.toString())
				.param("storeName", "카페 히스토리")
				.param("shiftInfo", "오늘 18:00-22:00")
				.param("reason", "급한 병원 일정"))
			.andExpect(status().is3xxRedirection());

		UUID substituteId = jdbcTemplate.queryForObject("""
			select id from substitute_requests
			where requester_id = ? order by created_at desc limit 1
			""", UUID.class, requesterId);

		// 다른 구직자가 대타를 맡는다.
		mockMvc.perform(post("/substitutes/{id}/take", substituteId).session(takerSession))
			.andExpect(status().is3xxRedirection());

		// 대타를 맡은 사람의 히스토리에 뜬다.
		mockMvc.perform(get("/mypage/matches").session(takerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("매칭 히스토리")))
			.andExpect(content().string(Matchers.containsString("카페 히스토리")))
			.andExpect(content().string(Matchers.containsString("대타")));

		// 대타를 구한(요청한) 사람의 히스토리에도 뜬다.
		mockMvc.perform(get("/mypage/matches").session(requesterSession))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("MH Taker")))
			.andExpect(content().string(Matchers.containsString("대타")));
	}

	@Test
	void matchHistoryShowsAcceptedMatch() throws Exception {
		MockHttpSession ownerSession = owner("MH Boss");
		MockHttpSession workerSession = jobSeeker("MH Worker");

		// 사장님 사업자 인증 (매칭 수락에 필요).
		mockMvc.perform(post("/boss/verify").session(ownerSession)
				.param("businessRegistrationNumber", "123-45-67890")
				.param("representativeName", "MH Boss")
				.param("openingDate", "2024-03-12")
				.param("storeName", "히스토리 식당")
				.param("storeAddress", "서울 마포구")
				.param("businessCategory", "외식")
				.param("storeIntroduction", "맛있는 가게"))
			.andExpect(status().is3xxRedirection());

		UUID ownerId = (UUID) ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);
		UUID workerId = (UUID) workerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);

		// 구직자가 지원 → 사장님이 수락.
		mockMvc.perform(post("/worker/match-requests").session(workerSession)
				.param("ownerId", ownerId.toString())
				.param("message", "히스토리 지원합니다."))
			.andExpect(status().is3xxRedirection());

		UUID requestId = jdbcTemplate.queryForObject("""
			select id from match_requests
			where job_seeker_id = ? and owner_id = ? and requested_by = 'JOB_SEEKER'
			order by created_at desc limit 1
			""", UUID.class, workerId, ownerId);

		mockMvc.perform(post("/boss/requests/{id}/accept", requestId).session(ownerSession))
			.andExpect(status().is3xxRedirection());

		// 구직자 히스토리에 매장명과 대화 버튼이 보인다.
		mockMvc.perform(get("/mypage/matches").session(workerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("히스토리 식당")))
			.andExpect(content().string(Matchers.containsString("정식 매칭")))
			.andExpect(content().string(Matchers.containsString("/chat/" + requestId)));

		// 사장님 히스토리에는 구직자 이름이 보인다.
		mockMvc.perform(get("/mypage/matches").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(Matchers.containsString("MH Worker")))
			.andExpect(content().string(Matchers.containsString("정식 매칭")));
	}
}
