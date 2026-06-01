package project.jjb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class JjbMvpSliceTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	JdbcTemplate jdbcTemplate;

	ObjectMapper objectMapper = new ObjectMapper();

	String runId;

	@BeforeEach
	void setRunId() {
		runId = UUID.randomUUID().toString();
	}

	@AfterEach
	void deleteTestData() {
		jdbcTemplate.update("""
			delete from match_requests
			where owner_id in (select id from members where social_subject like 'test-%')
			   or job_seeker_id in (select id from members where social_subject like 'test-%')
			""");
		jdbcTemplate.update("""
			delete from recruitments
			where owner_id in (select id from members where social_subject like 'test-%')
			""");
		jdbcTemplate.update("delete from members where social_subject like 'test-%'");
	}

	@Test
	void memberCanVerifyPhoneSelectRolesAndRegisterProfiles() throws Exception {
		UUID memberId = createMember("kakao", "test-job-seeker-" + runId, "Kim Alba");

		mockMvc.perform(put("/api/members/{memberId}/role", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"JOB_SEEKER"}
					"""))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("PHONE_VERIFICATION_REQUIRED"));

		verifyPhone(memberId);

		mockMvc.perform(put("/api/members/{memberId}/role", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"JOB_SEEKER"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeRole").value("JOB_SEEKER"))
			.andExpect(jsonPath("$.verification.phoneVerified").value(true));

		mockMvc.perform(put("/api/members/{memberId}/job-seeker-profile", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "availableTime": "today 18:00-23:00",
					  "preferredArea": "Seomyeon station 1km",
					  "desiredHourlyWage": 13000,
					  "experiencedIndustries": ["cafe", "beverage"],
					  "urgentSubstituteAvailable": true,
					  "introduction": "Cafe experience for one year."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.jobSeekerProfile.desiredHourlyWage").value(13000))
			.andExpect(jsonPath("$.jobSeekerProfile.urgentSubstituteAvailable").value(true));

		mockMvc.perform(put("/api/members/{memberId}/role", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"OWNER"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeRole").value("OWNER"))
			.andExpect(jsonPath("$.roles[?(@ == 'JOB_SEEKER')]").exists())
			.andExpect(jsonPath("$.roles[?(@ == 'OWNER')]").exists());

		mockMvc.perform(put("/api/members/{memberId}/owner-profile", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "storeName": "Cafe Momo",
					  "storeAddress": "Seoul Gangnam-gu",
					  "businessCategory": "cafe-dessert",
					  "storeIntroduction": "Near station."
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.ownerProfile.storeName").value("Cafe Momo"));
	}

	@Test
	void ownerModeAllowsExplorationButRecruitmentRequiresBusinessVerification() throws Exception {
		UUID ownerId = createPhoneVerifiedOwner();
		UUID jobSeekerId = createPhoneVerifiedJobSeeker();
		registerJobSeekerProfile(jobSeekerId);

		mockMvc.perform(post("/api/recruitments")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "title": "Urgent evening shift",
					  "workTime": "today 18:00-23:00",
					  "workplaceAddress": "Seoul Gangnam-gu",
					  "hourlyWage": 13000
					}
					""".formatted(ownerId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BUSINESS_VERIFICATION_REQUIRED"));

		mockMvc.perform(post("/api/match-requests")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "jobSeekerId": "%s",
					  "message": "Can you work this evening?"
					}
					""".formatted(ownerId, jobSeekerId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("BUSINESS_VERIFICATION_REQUIRED"));

		verifyBusiness(ownerId);

		mockMvc.perform(post("/api/recruitments")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "title": "Urgent evening shift",
					  "workTime": "today 18:00-23:00",
					  "workplaceAddress": "Seoul Gangnam-gu",
					  "hourlyWage": 13000
					}
					""".formatted(ownerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
			.andExpect(jsonPath("$.title").value("Urgent evening shift"));
	}

	@Test
	void verifiedOwnerCanRequestMatchAndJobSeekerCanAccept() throws Exception {
		UUID ownerId = createPhoneVerifiedOwner();
		verifyBusiness(ownerId);

		UUID jobSeekerId = createPhoneVerifiedJobSeeker();
		registerJobSeekerProfile(jobSeekerId);

		UUID matchRequestId = createMatchRequest(ownerId, jobSeekerId);

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/accept", matchRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"jobSeekerId":"%s"}
					""".formatted(jobSeekerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.jobSeekerId").value(jobSeekerId.toString()));
	}

	private UUID createPhoneVerifiedOwner() throws Exception {
		UUID ownerId = createMember("naver", "test-owner-" + UUID.randomUUID(), "Owner Kim");
		verifyPhone(ownerId);
		mockMvc.perform(put("/api/members/{memberId}/role", ownerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"OWNER"}
					"""))
			.andExpect(status().isOk());
		mockMvc.perform(put("/api/members/{memberId}/owner-profile", ownerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "storeName": "Cafe Momo",
					  "storeAddress": "Seoul Gangnam-gu",
					  "businessCategory": "cafe-dessert",
					  "storeIntroduction": "Near station."
					}
					"""))
			.andExpect(status().isOk());
		return ownerId;
	}

	private UUID createPhoneVerifiedJobSeeker() throws Exception {
		UUID jobSeekerId = createMember("apple", "test-worker-" + UUID.randomUUID(), "Worker Lee");
		verifyPhone(jobSeekerId);
		mockMvc.perform(put("/api/members/{memberId}/role", jobSeekerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"JOB_SEEKER"}
					"""))
			.andExpect(status().isOk());
		return jobSeekerId;
	}

	private UUID createMember(String provider, String subject, String displayName) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/members")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "socialProvider": "%s",
					  "socialSubject": "%s",
					  "displayName": "%s"
					}
					""".formatted(provider, subject, displayName)))
			.andExpect(status().isCreated())
			.andReturn();
		return readUuid(result, "id");
	}

	private void verifyPhone(UUID memberId) throws Exception {
		mockMvc.perform(post("/api/members/{memberId}/phone-verification", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "phoneNumber": "010-1234-5678",
					  "verificationCode": "123456"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verification.phoneVerified").value(true));
	}

	private void verifyBusiness(UUID ownerId) throws Exception {
		mockMvc.perform(post("/api/members/{memberId}/business-verification", ownerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "businessRegistrationNumber": "123-45-67890",
					  "representativeName": "Owner Kim",
					  "openingDate": "2024-03-12"
					}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.verification.businessVerified").value(true));
	}

	private void registerJobSeekerProfile(UUID jobSeekerId) throws Exception {
		mockMvc.perform(put("/api/members/{memberId}/job-seeker-profile", jobSeekerId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "availableTime": "today 18:00-23:00",
					  "preferredArea": "Seomyeon station 1km",
					  "desiredHourlyWage": 13000,
					  "experiencedIndustries": ["cafe", "beverage"],
					  "urgentSubstituteAvailable": true,
					  "introduction": "Cafe experience for one year."
					}
					"""))
			.andExpect(status().isOk());
	}

	private UUID createMatchRequest(UUID ownerId, UUID jobSeekerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/match-requests")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "jobSeekerId": "%s",
					  "message": "Can you work this evening?"
					}
					""".formatted(ownerId, jobSeekerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andReturn();
		return readUuid(result, "id");
	}

	private UUID readUuid(MvcResult result, String fieldName) throws Exception {
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get(fieldName).asText());
	}
}
