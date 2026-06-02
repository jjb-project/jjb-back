package project.jjb;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
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
			delete from reviews
			where evaluator_id in (select id from members where social_subject like 'test-%')
			   or target_id in (select id from members where social_subject like 'test-%')
			""");
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
	void localEmailAvailabilityChecksNormalizedDuplicateAndInvalidEmail() throws Exception {
		String email = "test-email-" + runId + "@example.test";

		mockMvc.perform(get("/api/members/email-availability")
				.param("email", "  " + email.toUpperCase() + "  "))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(true))
			.andExpect(jsonPath("$.normalizedEmail").value(email))
			.andExpect(jsonPath("$.message").value("사용 가능한 이메일입니다."));

		mockMvc.perform(post("/signup/local")
				.param("displayName", "Email Tester")
				.param("email", email)
				.param("password", "password123"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/api/members/email-availability")
				.param("email", email.toUpperCase()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.available").value(false))
			.andExpect(jsonPath("$.normalizedEmail").value(email))
			.andExpect(jsonPath("$.message").value("이미 가입된 이메일입니다."));

		mockMvc.perform(get("/api/members/email-availability")
				.param("email", "invalid-email"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_LOCAL_EMAIL"));
	}

	@Test
	void memberCanSelectRolesAndRegisterProfiles() throws Exception {
		UUID memberId = createMember("kakao", "test-job-seeker-" + runId, "Kim Alba");

		mockMvc.perform(put("/api/members/{memberId}/role", memberId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"role":"JOB_SEEKER"}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.activeRole").value("JOB_SEEKER"));

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
		UUID ownerId = createOwner();
		UUID jobSeekerId = createJobSeeker();
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
	void mvpSchemaDoesNotKeepPhoneVerificationColumns() {
		Integer phoneColumnCount = jdbcTemplate.queryForObject("""
			select count(*)
			from information_schema.columns
			where lower(table_name) = 'members'
			  and lower(column_name) in ('phone_number', 'phone_verified')
			""", Integer.class);

		assertThat(phoneColumnCount).isZero();
	}

	@Test
	void verifiedOwnerCanRequestMatchAndJobSeekerCanAccept() throws Exception {
		UUID ownerId = createOwner();
		verifyBusiness(ownerId);

		UUID jobSeekerId = createJobSeeker();
		registerJobSeekerProfile(jobSeekerId);

		UUID recruitmentId = createRecruitment(ownerId);

		mockMvc.perform(post("/api/match-requests")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "jobSeekerId": "%s",
					  "recruitmentId": "%s",
					  "message": "Can you work this evening?"
					}
					""".formatted(ownerId, ownerId, recruitmentId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SELF_MATCH_NOT_ALLOWED"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
				"/api/recruitments/{recruitmentId}/recommendations", recruitmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].jobSeekerId").value(jobSeekerId.toString()))
			.andExpect(jsonPath("$[0].score").isNumber())
			.andExpect(jsonPath("$[0].reason").isNotEmpty());

		mockMvc.perform(get("/api/members/{ownerId}/candidate-recommendations", ownerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].targetId").value(jobSeekerId.toString()))
			.andExpect(jsonPath("$[0].targetType").value("JOB_SEEKER"))
			.andExpect(jsonPath("$[0].score").isNumber())
			.andExpect(jsonPath("$[0].reason").value("GENAI 추천 사유"));

		mockMvc.perform(get("/api/members/{jobSeekerId}/store-recommendations", jobSeekerId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].targetId").value(ownerId.toString()))
			.andExpect(jsonPath("$[0].targetType").value("OWNER"))
			.andExpect(jsonPath("$[0].score").isNumber())
			.andExpect(jsonPath("$[0].reason").value("GENAI 추천 사유"));

		UUID wrongJobSeekerId = createJobSeeker();
		registerJobSeekerProfile(wrongJobSeekerId);
		UUID matchRequestId = createMatchRequest(ownerId, jobSeekerId, recruitmentId);

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/accept", matchRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"jobSeekerId":"%s"}
					""".formatted(wrongJobSeekerId)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("MATCH_REQUEST_NOT_ASSIGNED"));

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/accept", matchRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"jobSeekerId":"%s"}
					""".formatted(jobSeekerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.jobSeekerId").value(jobSeekerId.toString()));

		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "matchRequestId": "%s",
					  "evaluatorId": "%s",
					  "targetId": "%s",
					  "rating": 5,
					  "review": "Great communication and punctual."
					}
					""".formatted(matchRequestId, ownerId, jobSeekerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.targetId").value(jobSeekerId.toString()))
			.andExpect(jsonPath("$.rating").value(5));
	}

	@Test
	void matchDeclineCancelAndBidirectionalReviewsAreRecorded() throws Exception {
		UUID ownerId = createOwner();
		verifyBusiness(ownerId);

		UUID decliningJobSeekerId = createJobSeeker();
		registerJobSeekerProfile(decliningJobSeekerId);
		UUID declinedRequestId = createMatchRequest(ownerId, decliningJobSeekerId);

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/decline", declinedRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"jobSeekerId":"%s"}
					""".formatted(decliningJobSeekerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DECLINED"))
			.andExpect(jsonPath("$.respondedAt").isNotEmpty());

		UUID canceledJobSeekerId = createJobSeeker();
		registerJobSeekerProfile(canceledJobSeekerId);
		UUID canceledRequestId = createMatchRequest(ownerId, canceledJobSeekerId);

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/cancel", canceledRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"ownerId":"%s"}
					""".formatted(ownerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELED"))
			.andExpect(jsonPath("$.respondedAt").isNotEmpty());

		assertThat(matchRequestStatus(declinedRequestId)).isEqualTo("DECLINED");
		assertThat(matchRequestStatus(canceledRequestId)).isEqualTo("CANCELED");
		assertThat(respondedAtRecordedCount(declinedRequestId, canceledRequestId)).isEqualTo(2);

		UUID acceptedJobSeekerId = createJobSeeker();
		registerJobSeekerProfile(acceptedJobSeekerId);
		UUID acceptedRequestId = createMatchRequest(ownerId, acceptedJobSeekerId);

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/accept", acceptedRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"jobSeekerId":"%s"}
					""".formatted(acceptedJobSeekerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"));

		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "matchRequestId": "%s",
					  "evaluatorId": "%s",
					  "targetId": "%s",
					  "rating": 5,
					  "review": "Reliable and fast."
					}
					""".formatted(acceptedRequestId, ownerId, acceptedJobSeekerId)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "matchRequestId": "%s",
					  "evaluatorId": "%s",
					  "targetId": "%s",
					  "rating": 4,
					  "review": "Clear instructions."
					}
					""".formatted(acceptedRequestId, acceptedJobSeekerId, ownerId)))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "matchRequestId": "%s",
					  "evaluatorId": "%s",
					  "targetId": "%s",
					  "rating": 5,
					  "review": "Duplicate review."
					}
					""".formatted(acceptedRequestId, ownerId, acceptedJobSeekerId)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("REVIEW_ALREADY_EXISTS"));

		assertThat(reviewCountForMatch(acceptedRequestId)).isEqualTo(2);
	}

	@Test
	void jobSeekerCanRequestVerifiedOwnerAndOwnerCanAccept() throws Exception {
		UUID ownerId = createOwner();
		verifyBusiness(ownerId);

		UUID jobSeekerId = createJobSeeker();
		registerJobSeekerProfile(jobSeekerId);

		MvcResult result = mockMvc.perform(post("/api/match-requests/from-job-seeker")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "jobSeekerId": "%s",
					  "ownerId": "%s",
					  "message": "가게 조건이 좋아 보여서 지원합니다."
					}
					""".formatted(jobSeekerId, ownerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andExpect(jsonPath("$.requestedBy").value("JOB_SEEKER"))
			.andReturn();
		UUID matchRequestId = readUuid(result, "id");

		mockMvc.perform(post("/api/match-requests/{matchRequestId}/owner-accept", matchRequestId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"ownerId":"%s"}
					""".formatted(ownerId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ACCEPTED"))
			.andExpect(jsonPath("$.requestedBy").value("JOB_SEEKER"))
			.andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
			.andExpect(jsonPath("$.jobSeekerId").value(jobSeekerId.toString()));
	}

	private UUID createOwner() throws Exception {
		UUID ownerId = createMember("naver", "test-owner-" + UUID.randomUUID(), "Owner Kim");
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

	private UUID createJobSeeker() throws Exception {
		UUID jobSeekerId = createMember("apple", "test-worker-" + UUID.randomUUID(), "Worker Lee");
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
		return createMatchRequest(ownerId, jobSeekerId, null);
	}

	private UUID createMatchRequest(UUID ownerId, UUID jobSeekerId, UUID recruitmentId) throws Exception {
		String recruitmentJson = recruitmentId == null ? "" : """
			  "recruitmentId": "%s",
			""".formatted(recruitmentId);
		MvcResult result = mockMvc.perform(post("/api/match-requests")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "jobSeekerId": "%s",
					  %s
					  "message": "Can you work this evening?"
					}
					""".formatted(ownerId, jobSeekerId, recruitmentJson)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("REQUESTED"))
			.andReturn();
		return readUuid(result, "id");
	}

	private UUID createRecruitment(UUID ownerId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/recruitments")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					  "ownerId": "%s",
					  "title": "Urgent evening cafe shift",
					  "workDate": "2026-06-01",
					  "startTime": "18:00:00",
					  "endTime": "23:00:00",
					  "workplaceAddress": "Seoul Gangnam-gu",
					  "hourlyWage": 14000
					}
					""".formatted(ownerId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
			.andExpect(jsonPath("$.title").value("Urgent evening cafe shift"))
			.andReturn();
		return readUuid(result, "id");
	}

	private String matchRequestStatus(UUID matchRequestId) {
		return jdbcTemplate.queryForObject(
			"select status from match_requests where id = ?",
			String.class,
			matchRequestId
		);
	}

	private Integer respondedAtRecordedCount(UUID firstRequestId, UUID secondRequestId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from match_requests where id in (?, ?) and responded_at is not null",
			Integer.class,
			firstRequestId,
			secondRequestId
		);
	}

	private Integer reviewCountForMatch(UUID matchRequestId) {
		return jdbcTemplate.queryForObject(
			"select count(*) from reviews where match_request_id = ?",
			Integer.class,
			matchRequestId
		);
	}

	private UUID readUuid(MvcResult result, String fieldName) throws Exception {
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		return UUID.fromString(body.get(fieldName).asText());
	}

	@TestConfiguration
	static class FakeAiConfiguration {

		@Bean
		@Primary
		ChatModel fakeChatModel() {
			return new ChatModel() {
				@Override
				public ChatResponse call(Prompt prompt) {
					return new ChatResponse(List.of(new Generation(new AssistantMessage("GENAI 추천 사유"))));
				}
			};
		}
	}
}
