# 마이페이지 매칭 히스토리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 마이페이지에서 진입하는 `/mypage/matches` 전용 페이지에서, 로그인한 회원이 성사시킨 정식 매칭(ACCEPTED)과 대타 매칭(FILLED, 내가 맡은 것 + 내가 구한 것)을 최신순 단일 리스트로 보여준다.

**Architecture:** 기존 Spring MVC + Thymeleaf 서버 렌더링 패턴을 그대로 따른다. 신규 데이터 경로는 "내가 맡은 대타"를 조회하는 레포지토리/서비스 메서드 하나뿐이고, 나머지는 기존 조회 메서드(`listAcceptedMatchRequestsForParticipant`, `listSubstituteRequestsByRequester`)와 기존 헬퍼(`storeName`, `statusLabel`, `shortTime`)를 재사용한다. `JjbPageController`에 핸들러 + `MatchHistoryCard` record + 카드 빌더를 추가하고, `inbox.html`의 카드 스타일을 재사용하는 새 템플릿 `match_history.html`을 만든다.

**Tech Stack:** Java 26, Spring Boot 4, Spring MVC, Thymeleaf, Spring Data JPA, JUnit 5 + MockMvc, H2(테스트 `test` 프로파일, PostgreSQL 모드).

## Global Constraints

- 빌드 확인: `./gradlew compileJava`. 테스트 확인: `./gradlew test`.
- 테스트는 `@ActiveProfiles("test")`로 H2 인메모리 DB를 쓴다. 실제 PostgreSQL/GenAI 키에 의존하지 않는다.
- 서버 렌더링 화면은 `src/main/resources/templates`, 정적 자산은 `src/main/resources/static` 아래에 둔다.
- "성공한 매칭" = 정식 매칭 `MatchRequestStatus.ACCEPTED` + 대타 `SubstituteStatus.FILLED`. 거절/취소/진행중/OPEN은 히스토리에서 제외한다.
- 근무 "완료" 상태는 도메인에 없으므로 새로 도입하지 않는다.
- 기존 CSS 클래스(`inbox-card`, `inbox-status` 등)를 재사용하고, 신규 CSS는 배지 1개로 최소화한다.
- `/start`로 생성되는 테스트 회원의 `social_subject`는 항상 `mvc-` 접두사를 가진다. 테스트 정리는 `social_subject like 'mvc-%'` 기준으로 한다(기존 테스트와 동일).

---

## File Structure

- `src/main/java/project/jjb/matching/repository/persistence/SubstituteRequestJpaDataRepository.java` — Spring Data 쿼리 메서드 1개 추가
- `src/main/java/project/jjb/matching/repository/MatchingRepository.java` — 포트 메서드 시그니처 1개 추가
- `src/main/java/project/jjb/matching/repository/persistence/JpaMatchingRepository.java` — 위 메서드 구현
- `src/main/java/project/jjb/matching/service/MatchingService.java` — `listSubstituteRequestsByFiller` 추가
- `src/main/java/project/jjb/web/controller/JjbPageController.java` — `GET /mypage/matches` 핸들러 + `MatchHistoryCard` record + `matchHistoryCards` 빌더
- `src/main/resources/templates/match_history.html` — 신규 페이지
- `src/main/resources/templates/mypage.html` — 메뉴에 "매칭 히스토리" 링크 추가
- `src/main/resources/static/css/style.css` — `inbox-status--substitute` 배지 1줄 추가
- `src/test/java/project/jjb/MatchHistoryServiceTests.java` — 신규 서비스 테스트 (Task 1)
- `src/test/java/project/jjb/MatchHistoryPageTests.java` — 신규 페이지 통합 테스트 (Task 2)

---

## Task 1: 백엔드 — "내가 맡은 대타" 조회 경로 (repo + service)

**Files:**
- Test: `src/test/java/project/jjb/MatchHistoryServiceTests.java` (Create)
- Modify: `src/main/java/project/jjb/matching/repository/persistence/SubstituteRequestJpaDataRepository.java`
- Modify: `src/main/java/project/jjb/matching/repository/MatchingRepository.java`
- Modify: `src/main/java/project/jjb/matching/repository/persistence/JpaMatchingRepository.java`
- Modify: `src/main/java/project/jjb/matching/service/MatchingService.java`

**Interfaces:**
- Produces: `MatchingRepository.findSubstituteRequestsByFilledById(UUID filledById) -> List<SubstituteRequest>` (FILLED/그 외 상태 무관하게 filled_by_id 일치하는 전부, created_at desc).
- Produces: `MatchingService.listSubstituteRequestsByFiller(UUID memberId) -> List<SubstituteRequest>` (위 결과 중 `status == SubstituteStatus.FILLED`만, created_at desc 유지).
- Consumes (기존, 그대로 사용): `MatchingRepository.saveSubstituteRequest(SubstituteRequest) -> SubstituteRequest`, `SubstituteRequest.open(UUID requesterId, UUID ownerId, UUID recruitmentId, String storeName, String shiftInfo, String reason)`, `SubstituteRequest.fill(UUID takerId) -> SubstituteRequest`.

- [ ] **Step 1: 실패하는 서비스 테스트 작성**

Create `src/test/java/project/jjb/MatchHistoryServiceTests.java`:

```java
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
```

- [ ] **Step 2: 테스트 실행해서 컴파일 실패 확인**

Run: `./gradlew test --tests project.jjb.MatchHistoryServiceTests`
Expected: 컴파일 실패 — `listSubstituteRequestsByFiller(...)` 메서드가 `MatchingService`에 없음.

- [ ] **Step 3: Spring Data 쿼리 메서드 추가**

`src/main/java/project/jjb/matching/repository/persistence/SubstituteRequestJpaDataRepository.java`의 인터페이스 본문에 메서드 추가 (기존 `findByRequesterIdOrderByCreatedAtDesc` 아래):

```java
	List<SubstituteRequestJpaEntity> findByFilledByIdOrderByCreatedAtDesc(UUID filledById);
```

- [ ] **Step 4: 포트 인터페이스에 메서드 추가**

`src/main/java/project/jjb/matching/repository/MatchingRepository.java`에서 기존 `findSubstituteRequestsByRequesterId` 선언 아래에 추가:

```java
	List<SubstituteRequest> findSubstituteRequestsByFilledById(UUID filledById);
```

- [ ] **Step 5: JPA 구현 추가**

`src/main/java/project/jjb/matching/repository/persistence/JpaMatchingRepository.java`에서 기존 `findSubstituteRequestsByRequesterId` 메서드 아래에 추가:

```java
	@Override
	public List<SubstituteRequest> findSubstituteRequestsByFilledById(UUID filledById) {
		return substituteRequestJpaDataRepository.findByFilledByIdOrderByCreatedAtDesc(filledById).stream()
			.map(SubstituteRequestJpaEntity::toDomain)
			.toList();
	}
```

- [ ] **Step 6: 서비스 메서드 추가**

`src/main/java/project/jjb/matching/service/MatchingService.java`에서 기존 `listSubstituteRequestsByRequester` 메서드 아래에 추가:

```java
	@Transactional(readOnly = true)
	public List<SubstituteRequest> listSubstituteRequestsByFiller(UUID memberId) {
		return matchingRepository.findSubstituteRequestsByFilledById(memberId).stream()
			.filter(request -> request.status() == SubstituteStatus.FILLED)
			.toList();
	}
```

`SubstituteStatus`가 이미 import 되어 있는지 확인하고, 없으면 추가:

```java
import project.jjb.matching.domain.SubstituteStatus;
```

- [ ] **Step 7: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests project.jjb.MatchHistoryServiceTests`
Expected: PASS.

- [ ] **Step 8: 커밋**

```bash
git add src/main/java/project/jjb/matching src/test/java/project/jjb/MatchHistoryServiceTests.java
git commit -m "feat: 내가 맡은 대타 조회(listSubstituteRequestsByFiller) 추가

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: 매칭 히스토리 페이지 (컨트롤러 + 템플릿 + 메뉴 + CSS)

**Files:**
- Test: `src/test/java/project/jjb/MatchHistoryPageTests.java` (Create)
- Modify: `src/main/java/project/jjb/web/controller/JjbPageController.java`
- Create: `src/main/resources/templates/match_history.html`
- Modify: `src/main/resources/templates/mypage.html`
- Modify: `src/main/resources/static/css/style.css`

**Interfaces:**
- Consumes: `MatchingService.listAcceptedMatchRequestsForParticipant(UUID) -> List<MatchRequestSnapshot>`, `MatchingService.listSubstituteRequestsByFiller(UUID) -> List<SubstituteRequest>` (Task 1), `MatchingService.listSubstituteRequestsByRequester(UUID) -> List<SubstituteRequest>`, 기존 헬퍼 `storeName(MemberSnapshot)`, `statusLabel(String)`, `substituteStatusLabel(String)`, `shortTime(Instant)`, `requireMember(HttpSession)`.
- Produces: `GET /mypage/matches` → 뷰 `match_history`, 모델 속성 `historyCards: List<MatchHistoryCard>`. `MatchHistoryCard(String kindLabel, String kindCode, String counterpartName, String context, String detail, String statusLabel, String dateLabel, UUID chatMatchRequestId)`.

- [ ] **Step 1: 실패하는 페이지 통합 테스트 작성**

Create `src/test/java/project/jjb/MatchHistoryPageTests.java`:

```java
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
			where member_id in (select id from members where social_subject like 'mvc-%')
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
```

- [ ] **Step 2: 테스트 실행해서 실패 확인**

Run: `./gradlew test --tests project.jjb.MatchHistoryPageTests`
Expected: FAIL — `/mypage/matches`가 없어 404(콘텐츠 불일치) 또는 뷰 `match_history` 미존재.

- [ ] **Step 3: 컨트롤러에 핸들러 + record + 빌더 추가**

`src/main/java/project/jjb/web/controller/JjbPageController.java`:

(a) import 영역에 `SubstituteStatus`가 없으면 추가:

```java
import project.jjb.matching.domain.SubstituteStatus;
```

(b) 기존 `myPage` 핸들러(`@GetMapping("/mypage")`) 바로 아래에 새 핸들러 추가:

```java
	@GetMapping("/mypage/matches")
	String matchHistory(Model model, HttpSession session) {
		MemberSnapshot member = requireMember(session);
		model.addAttribute("member", member);
		model.addAttribute("activeRole", member.activeRole());
		model.addAttribute("historyCards", matchHistoryCards(member));
		return "match_history";
	}
```

(c) 기존 `inboxCard(...)` private 메서드 아래에 빌더 메서드 추가:

```java
	private List<MatchHistoryCard> matchHistoryCards(MemberSnapshot member) {
		UUID me = member.id();
		record Row(java.time.Instant sortKey, MatchHistoryCard card) {
		}
		List<Row> rows = new java.util.ArrayList<>();

		// 정식 매칭 (ACCEPTED)
		for (MatchRequestSnapshot request : matchingService.listAcceptedMatchRequestsForParticipant(me)) {
			boolean iAmOwner = request.ownerId().equals(me);
			UUID counterpartId = iAmOwner ? request.jobSeekerId() : request.ownerId();
			MemberSnapshot counterpart = memberService.getMember(counterpartId);
			String name = iAmOwner ? counterpart.displayName() : storeName(counterpart);
			String context = "";
			if (request.recruitmentId() != null) {
				try {
					context = matchingService.getRecruitment(request.recruitmentId()).title();
				}
				catch (Exception ignored) {
					context = "";
				}
			}
			if (context == null || context.isBlank()) {
				context = request.message();
			}
			java.time.Instant when = request.respondedAt() != null ? request.respondedAt() : request.createdAt();
			rows.add(new Row(when, new MatchHistoryCard(
				"정식 매칭", "MATCH", name, context, "",
				statusLabel(request.status().name()), shortTime(when), request.id())));
		}

		// 대타 — 내가 맡음
		for (SubstituteRequest substitute : matchingService.listSubstituteRequestsByFiller(me)) {
			String name = substitute.storeName() == null || substitute.storeName().isBlank()
				? memberService.getMember(substitute.ownerId()).displayName()
				: substitute.storeName();
			rows.add(new Row(substitute.createdAt(), new MatchHistoryCard(
				"대타 · 내가 맡음", "SUBSTITUTE", name, substitute.shiftInfo(), substitute.reason(),
				"매칭 완료", shortTime(substitute.createdAt()), null)));
		}

		// 대타 — 내가 구함 (FILLED)
		for (SubstituteRequest substitute : matchingService.listSubstituteRequestsByRequester(me)) {
			if (substitute.status() != SubstituteStatus.FILLED) {
				continue;
			}
			String takerName = substitute.filledById() == null
				? "대타자"
				: memberService.getMember(substitute.filledById()).displayName();
			String store = substitute.storeName() == null ? "" : substitute.storeName();
			String context = store.isBlank()
				? substitute.shiftInfo()
				: store + " · " + substitute.shiftInfo();
			rows.add(new Row(substitute.createdAt(), new MatchHistoryCard(
				"대타 · 구함 완료", "SUBSTITUTE", takerName + " 님", context, substitute.reason(),
				"매칭 완료", shortTime(substitute.createdAt()), null)));
		}

		return rows.stream()
			.sorted(java.util.Comparator.comparing(Row::sortKey).reversed())
			.map(Row::card)
			.toList();
	}
```

(d) 기존 `record InboxCard(...)` 선언 아래에 새 record 추가:

```java
	record MatchHistoryCard(String kindLabel, String kindCode, String counterpartName, String context, String detail, String statusLabel, String dateLabel, UUID chatMatchRequestId) {
	}
```

- [ ] **Step 4: 템플릿 `match_history.html` 작성**

Create `src/main/resources/templates/match_history.html`:

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('매칭 히스토리')}"></head>
<body class="web-page">
<div th:replace="~{fragments/layout :: siteHeader}"></div>

<div class="web-detail">
  <div class="inbox-head">
    <h1><i class="fa-solid fa-handshake"></i> 매칭 히스토리</h1>
    <span class="section-sub">성사된 매칭 모아보기</span>
  </div>

  <div class="section-header">
    <span class="section-title">성공한 매칭</span>
    <span class="section-more" th:text="${#lists.size(historyCards)} + '건'">0건</span>
  </div>
  <div class="empty-state" th:if="${#lists.isEmpty(historyCards)}">아직 성공한 매칭이 없습니다.</div>
  <div class="inbox-card-list">
    <article class="inbox-card" th:each="c : ${historyCards}">
      <div class="inbox-card-main">
        <div class="inbox-card-top">
          <strong class="inbox-name" th:text="${c.counterpartName}">상대방</strong>
          <span class="inbox-status"
                th:classappend="${c.kindCode == 'SUBSTITUTE'} ? ' inbox-status--substitute' : ' inbox-status--accepted'"
                th:text="${c.kindLabel}">종류</span>
        </div>
        <p class="inbox-context" th:if="${c.context != null and !c.context.isEmpty()}" th:text="'· ' + ${c.context}">공고</p>
        <p class="inbox-message" th:if="${c.detail != null and !c.detail.isEmpty()}" th:text="${c.detail}">상세</p>
        <p class="inbox-context" th:text="${c.statusLabel} + ' · ' + ${c.dateLabel}">상태 · 날짜</p>
      </div>
      <div class="inbox-card-actions" th:if="${c.chatMatchRequestId != null}">
        <a class="btn btn-primary btn-sm" th:href="@{/chat/{id}(id=${c.chatMatchRequestId})}"><i class="fa-regular fa-comment"></i> 대화</a>
      </div>
    </article>
  </div>
</div>

<div th:replace="~{fragments/layout :: scripts}"></div>
</body>
</html>
```

- [ ] **Step 5: 마이페이지 메뉴에 링크 추가**

`src/main/resources/templates/mypage.html`의 `.menu-list` 안, 기존 `평가하기` 메뉴 항목(`<a ... th:href="@{/eval}">`) 바로 아래에 추가:

```html
    <a class="menu-item" th:href="@{/mypage/matches}">
      <i class="fa-solid fa-handshake"></i>
      매칭 히스토리
    </a>
```

- [ ] **Step 6: 대타 배지 CSS 추가**

`src/main/resources/static/css/style.css`의 `.inbox-status--canceled{...}` 줄 바로 아래에 추가:

```css
.inbox-status--substitute{background:var(--blue-light);color:var(--blue)}
```

- [ ] **Step 7: 테스트 실행해서 통과 확인**

Run: `./gradlew test --tests project.jjb.MatchHistoryPageTests`
Expected: PASS (두 테스트 모두).

- [ ] **Step 8: 전체 빌드/테스트 확인**

Run: `./gradlew compileJava test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/project/jjb/web/controller/JjbPageController.java \
        src/main/resources/templates/match_history.html \
        src/main/resources/templates/mypage.html \
        src/main/resources/static/css/style.css \
        src/test/java/project/jjb/MatchHistoryPageTests.java
git commit -m "feat: 마이페이지 매칭 히스토리 페이지 추가 (정식+대타 매칭)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review 결과

**Spec coverage:**
- 진입점(마이페이지 메뉴 → `/mypage/matches`): Task 2 Step 5 + Step 3(b). ✅
- 정식 매칭(ACCEPTED) 표시 + 대화 버튼: Task 2 Step 3(c) 정식 루프, Step 4 템플릿. ✅
- 대타(내가 맡은 `filledById=me`): Task 1(조회) + Task 2 Step 3(c). ✅
- 대타(내가 구한 `requesterId=me` & FILLED): Task 2 Step 3(c) 세 번째 루프(FILLED 필터). ✅
- 최신순 통합 정렬: Task 2 Step 3(c) `Row.sortKey` 역순 정렬. ✅
- 종류 배지 구분: `kindCode` + CSS(Step 6). ✅
- empty-state / N건 카운트: Task 2 Step 4 템플릿. ✅
- 거절/취소/OPEN 제외: ACCEPTED만(서비스 기존 메서드), 대타는 FILLED만 필터. ✅
- 신규 레포 메서드: Task 1. ✅

**Placeholder scan:** "TBD"/"TODO"/"적절히 처리" 류 없음. 모든 코드 블록은 실제 구현. ✅

**Type consistency:**
- `findSubstituteRequestsByFilledById` / `findByFilledByIdOrderByCreatedAtDesc` / `listSubstituteRequestsByFiller` 시그니처가 Task 1 전반에서 일치. ✅
- `MatchHistoryCard` 8개 필드(`kindLabel, kindCode, counterpartName, context, detail, statusLabel, dateLabel, chatMatchRequestId`)가 빌더 생성자 호출, record 선언, 템플릿 접근(`c.*`)에서 일치. ✅
- `shortTime(Instant)`는 기존 시그니처(MM/dd HH:mm) 그대로 사용. ✅
```
