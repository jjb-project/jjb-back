package project.jjb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
class JjbMvcPageTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@AfterEach
	void deleteMvcData() {
		jdbcTemplate.update("""
			delete from reviews
			where evaluator_id in (select id from members where social_subject like 'mvc-%' or social_subject like 'fixture-%')
			   or target_id in (select id from members where social_subject like 'mvc-%' or social_subject like 'fixture-%')
			""");
		jdbcTemplate.update("""
			delete from match_requests
			where owner_id in (select id from members where social_subject like 'mvc-%' or social_subject like 'fixture-%')
			   or job_seeker_id in (select id from members where social_subject like 'mvc-%' or social_subject like 'fixture-%')
			""");
		jdbcTemplate.update("""
			delete from recruitments
			where owner_id in (select id from members where social_subject like 'mvc-%' or social_subject like 'fixture-%')
			""");
		jdbcTemplate.update("delete from members where social_subject like 'mvc-%' or social_subject like 'fixture-%'");
	}

	@Test
	void mainMvcPagesRender() throws Exception {
		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("급할 때 바로 매칭")))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("/oauth2/authorization/google"))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/login/local")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/signup")))
			.andExpect(content().string(org.hamcrest.Matchers.not(
				org.hamcrest.Matchers.containsString("id=\"signupEmail\""))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/kakao")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/naver")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js")));

		mockMvc.perform(get("/signup"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("이메일 회원가입")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/signup/local")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("중복확인")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-email-check-button")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-signup-submit")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("disabled")));

		mockMvc.perform(get("/role"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		mockMvc.perform(get("/worker/home"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));

		mockMvc.perform(get("/boss/home"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"));
	}

	@Test
	void oauth2AuthorizationEndpointsRedirectToProviders() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/kakao"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("kauth.kakao.com")));

		mockMvc.perform(get("/oauth2/authorization/naver"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("nid.naver.com")));
	}

	@Test
	void localSignupAndLoginUseSession() throws Exception {
		String email = "mvc-local-" + UUID.randomUUID() + "@example.test";
		String password = "password123";
		MockHttpSession signupSession = new MockHttpSession();

		mockMvc.perform(post("/signup/local")
				.session(signupSession)
				.param("displayName", "Local Tester")
				.param("email", email)
				.param("password", password))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/role"))
			.andExpect(request().sessionAttribute(
				WebSessionKeys.CURRENT_MEMBER_ID,
				org.hamcrest.Matchers.notNullValue()
			));

		mockMvc.perform(post("/login/local")
				.session(new MockHttpSession())
				.param("email", email)
				.param("password", password))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/role"))
			.andExpect(request().sessionAttribute(
				WebSessionKeys.CURRENT_MEMBER_ID,
				org.hamcrest.Matchers.notNullValue()
			));

		mockMvc.perform(post("/login/local")
				.session(new MockHttpSession())
				.param("email", email)
				.param("password", "wrong-password"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/"))
			.andExpect(flash().attribute("errorMessage", "이메일 또는 비밀번호가 올바르지 않습니다."));
	}

	@Test
	void formPostFlowCreatesSessionAndSavesWorkerProfile() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/start")
				.session(session)
				.param("socialProvider", "kakao")
				.param("displayName", "MVC Tester"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/role"))
			.andExpect(header().string("Location", "/role"));

		mockMvc.perform(post("/role")
				.session(session)
				.param("role", "JOB_SEEKER"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/worker/home"));

		mockMvc.perform(get("/worker/profile/new").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"availableTime\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-available-time-input")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("type=\"time\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"18:00\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"23:00\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"10320\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("min=\"10320\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("2026년 최저시급 10,320원이 기본값으로 입력돼 있어요.")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-area-option")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-industry-option")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("서울 강남구")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("고객응대")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("checked=\"checked\"")));

		mockMvc.perform(post("/worker/profile")
				.session(session)
				.param("availableTime", "오늘 17:00-22:00")
				.param("preferredArea", "서울 강남구")
				.param("desiredHourlyWage", "13000")
				.param("experiencedIndustries", "고객응대, 정리")
				.param("urgentSubstituteAvailable", "true")
				.param("introduction", "MVC form profile"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/worker/home"));

		mockMvc.perform(get("/worker/profile/edit").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC form profile")));

		mockMvc.perform(get("/worker/home").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-live-refresh")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("AI 추천받기")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-recommendation-url=\"/api/members/")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("모드 전환")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/role\"")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("fa-regular fa-square-plus")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fa-regular fa-address-card"))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("등록 내역")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("오늘 17:00-22:00")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("서울 강남구")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("13,000원 이상")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("고객응대, 정리")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC form profile")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("김하나"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("김알바"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("카페 모모"))));

		MockHttpSession ownerSession = new MockHttpSession();
		mockMvc.perform(post("/start")
				.session(ownerSession)
				.param("socialProvider", "naver")
				.param("displayName", "MVC Owner"))
			.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/role")
				.session(ownerSession)
				.param("role", "OWNER"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/boss/home").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC Tester")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("data-live-refresh")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("구직자가 등록한 프로필")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("모집 등록"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("진행 중 모집"))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("모드 전환")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/role\"")))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fa-regular fa-square-plus"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fa-regular fa-address-card"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("김하나"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("김알바"))))
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("카페 모모"))));

		mockMvc.perform(get("/boss/recruitments/new").session(ownerSession))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/boss/verify"))
			.andExpect(flash().attribute("errorMessage", "사업자 인증이 필요합니다."));

		mockMvc.perform(get("/boss/verify").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-fill-target"))))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("업종을 직접 입력해주세요")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("구직자에게는 이렇게 보여요")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("예: 역 근처라 출퇴근이 편하고, 처음 오신 분도 쉽게 배울 수 있어요.")));

		UUID workerId = (UUID) session.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID);
		mockMvc.perform(get("/boss/candidates/{id}", workerId).session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("매칭 요청은 사업자 인증 후 보낼 수 있습니다.")));

		mockMvc.perform(post("/boss/match-requests")
				.session(ownerSession)
				.param("jobSeekerId", workerId.toString())
				.param("message", "오늘 근무 가능하실까요?"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/boss/verify"))
			.andExpect(flash().attribute("errorMessage", "사업자 인증이 필요합니다."));

		mockMvc.perform(post("/boss/verify")
				.session(ownerSession)
				.param("businessRegistrationNumber", "123-45-67890")
				.param("representativeName", "MVC Owner")
				.param("openingDate", "2024-03-12")
				.param("storeName", "카페 양방향")
				.param("storeAddress", "서울 마포구")
				.param("businessCategory", "카페")
				.param("storeIntroduction", "초보도 환영하는 따뜻한 매장입니다."))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/boss/home"));

		mockMvc.perform(get("/worker/home").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("지원 가능한 가게")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("카페 양방향")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("AI 추천받기")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("초보도 환영하는 따뜻한 매장입니다.")));

		mockMvc.perform(post("/worker/match-requests")
				.session(session)
				.param("ownerId", ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID).toString())
				.param("message", "근무 지원합니다."))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrlPattern("/match-confirmed?matchRequestId=*"));

		UUID workerRequestId = jdbcTemplate.queryForObject("""
			select id
			from match_requests
			where job_seeker_id = ?
			  and owner_id = ?
			  and requested_by = 'JOB_SEEKER'
			""", UUID.class, workerId, ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID));

		mockMvc.perform(get("/boss/home").session(ownerSession))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("받은 지원 요청")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC Tester")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("근무 지원합니다.")));

		mockMvc.perform(get("/worker/home").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("보낸 지원 요청")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("취소")));

		mockMvc.perform(post("/worker/requests/{id}/cancel", workerRequestId).session(session))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/worker/home"));

		Integer canceledCount = jdbcTemplate.queryForObject("""
			select count(*)
			from match_requests
			where id = ?
			  and status = 'CANCELED'
			  and requested_by = 'JOB_SEEKER'
			""", Integer.class, workerRequestId);
		org.assertj.core.api.Assertions.assertThat(canceledCount).isEqualTo(1);

		UUID secondWorkerRequestId = createWorkerRequest(session, ownerSession);
		mockMvc.perform(post("/boss/requests/{id}/accept", secondWorkerRequestId).session(ownerSession))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/match-confirmed?matchRequestId=" + secondWorkerRequestId));
	}

	private UUID createWorkerRequest(MockHttpSession workerSession, MockHttpSession ownerSession) throws Exception {
		mockMvc.perform(post("/worker/match-requests")
				.session(workerSession)
				.param("ownerId", ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID).toString())
				.param("message", "다시 지원합니다."))
			.andExpect(status().is3xxRedirection());

		return jdbcTemplate.queryForObject("""
			select id
			from match_requests
			where job_seeker_id = ?
			  and owner_id = ?
			  and requested_by = 'JOB_SEEKER'
			  and status = 'REQUESTED'
			order by created_at desc
			limit 1
			""", UUID.class, workerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID), ownerSession.getAttribute(WebSessionKeys.CURRENT_MEMBER_ID));
	}
}
