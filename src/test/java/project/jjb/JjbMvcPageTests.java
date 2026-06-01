package project.jjb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/google")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/oauth2/authorization/apple")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/js/app.js")));

		mockMvc.perform(get("/phone"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("휴대폰 본인 인증")));

		mockMvc.perform(get("/role"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("이용 목적 선택")));

		mockMvc.perform(get("/worker/home"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("받은 매칭 요청")));

		mockMvc.perform(get("/boss/home"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("추천 인재")));
	}

	@Test
	void oauth2AuthorizationEndpointsRedirectToProviders() throws Exception {
		mockMvc.perform(get("/oauth2/authorization/google"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("accounts.google.com")));

		mockMvc.perform(get("/oauth2/authorization/apple"))
			.andExpect(status().is3xxRedirection())
			.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("appleid.apple.com")));
	}

	@Test
	void formPostFlowCreatesSessionAndSavesWorkerProfile() throws Exception {
		MockHttpSession session = new MockHttpSession();

		mockMvc.perform(post("/start")
				.session(session)
				.param("socialProvider", "kakao")
				.param("displayName", "MVC Tester"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/phone"))
			.andExpect(header().string("Location", "/phone"));

		mockMvc.perform(post("/phone")
				.session(session)
				.param("phoneNumber", "010-1234-5678")
				.param("verificationCode", "123456"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/role"));

		mockMvc.perform(post("/role")
				.session(session)
				.param("role", "JOB_SEEKER"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/worker/home"));

		mockMvc.perform(post("/worker/profile")
				.session(session)
				.param("availableTime", "오늘 17:00-22:00")
				.param("preferredArea", "서울 강남구")
				.param("desiredHourlyWage", "13000")
				.param("experiencedIndustries", "카페, 서빙")
				.param("urgentSubstituteAvailable", "true")
				.param("introduction", "MVC form profile"))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/worker/home"));

		mockMvc.perform(get("/worker/profile/edit").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC form profile")));

		mockMvc.perform(get("/boss/home").session(new MockHttpSession()))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("MVC Tester")));
	}
}
