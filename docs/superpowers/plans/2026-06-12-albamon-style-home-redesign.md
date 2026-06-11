# 알바천국 스타일 홈 화면 + 공고 전체보기 (1단계) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** "바로알바" 홈 화면을 알바천국 스타일 데스크톱 레이아웃(공통 헤더/GNB +
3단 히어로 + 카테고리 그리드)으로 개편하고, 신규 "실시간 공고 전체보기"
(`/jobs`) 페이지를 추가하여 양방향 매칭 기능 진입점을 강화한다.

**Architecture:** `JjbPageController`에 공개(`permitAll`) 라우트 `/jobs`와
`JobListingCard` DTO/헬퍼를 추가해 OPEN 상태 공고를 매장명과 함께 카드로
노출한다. `fragments/layout.html`에 재사용 가능한 `siteHeader` Thymeleaf
프래그먼트를 추가하고, `index.html`과 신규 `jobs.html`에서 이를 사용한다.
기존 초록/파랑 색상 변수와 `.job-portal-shell`(1180px) 컨테이너 패턴을
그대로 활용하고, `style.css`에 헤더/히어로/카테고리/잡 그리드용 CSS를
추가한다.

**Tech Stack:** Spring Boot (Thymeleaf MVC), MockMvc 통합 테스트, 순수 CSS
(기존 `style.css` 확장).

---

## File Structure

- Modify: `src/main/java/project/jjb/web/controller/JjbPageController.java`
  - `JobListingCard` record, `jobListingCards`/`jobListingCard`/`openRecruitments`
    헬퍼, `start()`에 `recentJobs` 추가, 신규 `GET /jobs` 핸들러
- Create: `src/main/resources/templates/jobs.html`
  - 신규 "실시간 공고 전체보기" 페이지 (카드 그리드)
- Modify: `src/main/resources/templates/fragments/layout.html`
  - `siteHeader` 프래그먼트 추가 (로고 + 통합검색 + GNB)
- Modify: `src/main/resources/templates/index.html`
  - `siteHeader` 사용 + 3단 히어로 그리드 + 3열 카테고리 그리드로 전면 개편
- Modify: `src/main/resources/static/css/style.css`
  - `.site-header`, `.site-gnb`, `.home-hero-grid`, `.home-category-grid`,
    `.jobs-grid` 등 신규 클래스 추가
- Modify: `src/test/java/project/jjb/JjbMvcPageTests.java`
  - `/jobs` 페이지 및 홈 신규 섹션에 대한 MockMvc 테스트 추가

---

### Task 1: 백엔드 - `JobListingCard` DTO, 헬퍼, `/jobs` 라우트, 홈 `recentJobs`

**Files:**
- Modify: `src/main/java/project/jjb/web/controller/JjbPageController.java:66-70` (start 메서드)
- Modify: `src/main/java/project/jjb/web/controller/JjbPageController.java:606-621` (헬퍼 영역)
- Modify: `src/main/java/project/jjb/web/controller/JjbPageController.java:818-819` (record 영역)
- Create: `src/main/resources/templates/jobs.html` (최소 placeholder, Task 5에서 완성)
- Test: `src/test/java/project/jjb/JjbMvcPageTests.java`

- [ ] **Step 1: 최소 `jobs.html` placeholder 작성**

테스트가 뷰 렌더링 단계에서 실패하지 않도록 먼저 최소 템플릿을 만든다
(Task 5에서 카드 그리드로 완성).

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('실시간 공고')}"></head>
<body>
<main class="app-shell job-portal-shell public-portal">
  <section class="screen active no-bottom">
    <div class="jobs-page-head">
      <h1>실시간 등록 공고</h1>
      <span class="section-sub" th:text="${#lists.size(jobCards)} + '개의 공고'">0개의 공고</span>
    </div>
    <div class="empty-state" th:if="${#lists.isEmpty(jobCards)}">아직 등록된 공고가 없습니다.</div>
    <article th:each="job : ${jobCards}">
      <strong th:text="${job.storeName}">매장명</strong>
      <span th:text="${job.title}">공고 제목</span>
    </article>
  </section>
</main>
<div th:replace="~{fragments/layout :: scripts}"></div>
</body>
</html>
```

- [ ] **Step 2: 실패하는 MockMvc 테스트 작성**

`src/test/java/project/jjb/JjbMvcPageTests.java`의 `mainMvcPagesRender`
테스트 메서드 **다음**(클래스 내부)에 새 테스트 메서드를 추가한다. 기존
`createWorkerRequest` 류 헬퍼와 같은 클래스 안에 둔다.

```java
	@Test
	void jobsPageListsOpenRecruitments() throws Exception {
		MockHttpSession ownerSession = new MockHttpSession();
		mockMvc.perform(post("/start")
				.session(ownerSession)
				.param("socialProvider", "naver")
				.param("displayName", "Jobs Owner"))
			.andExpect(status().is3xxRedirection());
		mockMvc.perform(post("/role")
				.session(ownerSession)
				.param("role", "OWNER"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(post("/boss/verify")
				.session(ownerSession)
				.param("businessRegistrationNumber", "123-45-67891")
				.param("representativeName", "Jobs Owner")
				.param("openingDate", "2024-03-12")
				.param("storeName", "잡스카페")
				.param("storeAddress", "서울 강남구")
				.param("businessCategory", "카페")
				.param("storeIntroduction", "공고 목록 테스트용 매장입니다."))
			.andExpect(status().is3xxRedirection())
			.andExpect(redirectedUrl("/boss/home"));

		mockMvc.perform(post("/boss/recruitments")
				.session(ownerSession)
				.param("title", "오픈 타임 홀 서빙")
				.param("workDate", "2024-04-01")
				.param("startTime", "09:00")
				.param("endTime", "13:00")
				.param("workplaceAddress", "서울 강남구 테헤란로 1")
				.param("hourlyWage", "11000"))
			.andExpect(status().is3xxRedirection());

		mockMvc.perform(get("/jobs"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("잡스카페")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("오픈 타임 홀 서빙")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("11,000원/시간")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("/css/style.css")));

		mockMvc.perform(get("/"))
			.andExpect(status().isOk())
			.andExpect(content().string(org.hamcrest.Matchers.containsString("실시간 등록 공고")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("잡스카페")));
	}
```

`/start` 호출 시 사용하는 `socialProvider`/`displayName` 조합으로 생성된
회원의 `social_subject`가 `mvc-` 또는 `fixture-`로 시작해야 `@AfterEach`
정리 쿼리가 데이터를 지운다. `start()`/`startSession` 구현을 확인하여
`socialProvider="naver"`, `displayName="Jobs Owner"` 조합이 기존
`MVC Owner` 케이스(라인 233 부근)와 동일하게 `mvc-`/`fixture-` 접두사
social_subject를 생성하는지 확인한다. 만약 접두사 규칙이 다르면, 기존
`MVC Owner`/`MVC Tester` 테스트와 동일한 방식(같은 `socialProvider` 값,
다른 `displayName`)을 그대로 따른다 — 새 테스트 전용 식별자가 필요하면
`displayName`만 `"Jobs Owner"`로 바꾸는 정도로 충분하다(접두사는
`socialProvider`/세션 로직에서 결정됨).

- [ ] **Step 3: 테스트 실행하여 실패 확인**

Run: `./gradlew test --tests "project.jjb.JjbMvcPageTests.jobsPageListsOpenRecruitments"`
Expected: FAIL — `404` for `GET /jobs` (No mapping found) 또는 `jobCards`
모델 속성 누락으로 인한 템플릿 렌더링 오류, 그리고 `/`에 "실시간 등록 공고"
문자열이 없어서 실패.

- [ ] **Step 4: `JobListingCard` record 추가**

`src/main/java/project/jjb/web/controller/JjbPageController.java:818-819`
(`RecruitmentCard` record 바로 다음)에 추가:

```java
	record JobListingCard(UUID id, String title, String storeName, String businessCategory, String workplaceAddress, String workTime, int hourlyWage, String status) {
	}
```

- [ ] **Step 5: `openRecruitments`/`jobListingCard`/`jobListingCards` 헬퍼 추가**

`src/main/java/project/jjb/web/controller/JjbPageController.java:606-621`
(`recruitmentCards`/`recruitmentCard` 메서드 바로 다음)에 추가:

```java
	private List<Recruitment> openRecruitments() {
		return matchingService.listRecruitments().stream()
			.filter(Recruitment::isOpen)
			.toList();
	}

	private List<JobListingCard> jobListingCards(List<Recruitment> recruitments) {
		return recruitments.stream()
			.map(this::jobListingCard)
			.toList();
	}

	private JobListingCard jobListingCard(Recruitment recruitment) {
		MemberSnapshot owner = memberService.getMember(recruitment.ownerId());
		OwnerProfile profile = owner.ownerProfile();
		return new JobListingCard(
			recruitment.id(),
			recruitment.title(),
			storeName(owner),
			profile == null ? "" : profile.businessCategory(),
			recruitment.workplaceAddress(),
			recruitment.workTime(),
			recruitment.hourlyWage(),
			statusLabel(recruitment.status().name())
		);
	}
```

- [ ] **Step 6: `start()`에 `recentJobs` 추가, `/jobs` 라우트 추가**

`src/main/java/project/jjb/web/controller/JjbPageController.java:66-70`을
다음으로 교체:

```java
	@GetMapping("/")
	String start(Model model, HttpSession session) {
		addSessionMember(model, session);
		model.addAttribute("recentJobs", jobListingCards(openRecruitments().stream().limit(5).toList()));
		return "index";
	}

	@GetMapping("/jobs")
	String jobs(Model model, HttpSession session) {
		addSessionMember(model, session);
		model.addAttribute("jobCards", jobListingCards(openRecruitments()));
		return "jobs";
	}
```

- [ ] **Step 7: 테스트 실행하여 통과 확인**

Run: `./gradlew test --tests "project.jjb.JjbMvcPageTests.jobsPageListsOpenRecruitments"`
Expected: PASS

- [ ] **Step 8: 전체 테스트 실행 (회귀 확인)**

Run: `./gradlew test`
Expected: PASS (기존 `mainMvcPagesRender` 등도 모두 통과)

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/project/jjb/web/controller/JjbPageController.java \
        src/main/resources/templates/jobs.html \
        src/test/java/project/jjb/JjbMvcPageTests.java
git commit -m "feat: 실시간 공고 전체보기(/jobs) 라우트와 홈 최근 공고 데이터 추가"
```

---

### Task 2: 공통 헤더/GNB CSS 추가

**Files:**
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: 파일 끝에 신규 CSS 블록 추가**

`src/main/resources/static/css/style.css` 맨 끝(파일 마지막 줄, 현재 989줄
`}` 다음)에 아래 블록을 추가한다.

```css

/* ===== Albamon-style site header / home redesign ===== */
.site-header {
  background: white;
  border-bottom: 1px solid var(--gray-200);
}
.site-header-top {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 14px 24px;
  flex-wrap: wrap;
}
.site-header-top .logo-mark {
  flex-shrink: 0;
}
.site-search {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 280px;
  max-width: 620px;
}
.site-search .search-field {
  flex: 1.4;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 14px;
  border: 2px solid var(--navy);
  border-radius: 6px;
  background: white;
  min-width: 0;
}
.site-search .search-field i {
  color: var(--blue);
}
.site-search .search-field input {
  width: 100%;
  border: 0;
  outline: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--ink);
}
.site-search .filter-input {
  flex: 1;
  min-width: 0;
  height: 44px;
  padding: 0 10px;
  border: 1px solid #CBD5E1;
  border-radius: 6px;
  background: #F8FAFC;
  font-size: 13px;
  font-weight: 700;
}
.site-header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
  white-space: nowrap;
}
.site-header-actions a:not(.mode-switch-link):not(.avatar) {
  font-size: 13px;
  font-weight: 800;
  color: var(--gray-600);
}
.site-gnb {
  background: var(--navy);
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px;
  flex-wrap: wrap;
}
.site-gnb-menu {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.site-gnb-menu a {
  min-height: 44px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  color: white;
  font-size: 14px;
  font-weight: 800;
}
.site-gnb-menu a:last-child {
  color: var(--green-mid);
}
.site-gnb-actions {
  display: flex;
  gap: 8px;
  padding: 8px 0;
}
.gnb-btn {
  padding: 8px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 800;
  color: white;
  white-space: nowrap;
}
.gnb-btn-worker {
  background: var(--green);
}
.gnb-btn-boss {
  background: var(--blue);
}

/* Home hero (3-column) */
.home-hero-grid {
  max-width: 1180px;
  margin: 0 auto;
  padding: 20px 24px;
  display: grid;
  grid-template-columns: 1fr 1.4fr 1fr;
  gap: 16px;
  align-items: stretch;
}
.hero-jobs-card,
.hero-banner-card {
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  padding: 16px;
}
.hero-jobs-card h2 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 800;
  margin-bottom: 10px;
}
.hero-jobs-card h2 a {
  font-size: 12px;
  font-weight: 700;
  color: var(--green);
}
.hero-job-item {
  display: block;
  padding: 10px 0;
  border-bottom: 1px solid var(--gray-100);
}
.hero-job-item:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.hero-job-item strong {
  display: block;
  font-size: 13px;
  font-weight: 800;
  color: var(--ink);
  margin-bottom: 3px;
}
.hero-job-item span {
  display: block;
  font-size: 12px;
  color: var(--gray-600);
  line-height: 1.5;
}
.hero-job-wage {
  color: var(--green-dark);
  font-weight: 800;
}
.hero-banner-card {
  background: linear-gradient(135deg, var(--green) 0%, var(--green-dark) 100%);
  color: white;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  text-align: left;
}
.hero-banner-card .eyebrow {
  font-size: 12px;
  font-weight: 800;
  opacity: 0.85;
}
.hero-banner-card h1 {
  font-size: 22px;
  font-weight: 800;
  line-height: 1.35;
}
.hero-banner-card p {
  font-size: 13px;
  line-height: 1.6;
  opacity: 0.92;
}
.hero-banner-card .btn {
  width: auto;
  align-self: flex-start;
}
.home-hero-grid .public-login-card {
  margin: 0;
}

/* Category grid */
.home-category-grid {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px 24px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}
.category-box {
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  padding: 16px;
}
.category-box h3 {
  font-size: 14px;
  font-weight: 800;
  margin-bottom: 12px;
}
.category-box-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
}
.category-box-grid a {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--gray-100);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 700;
  color: var(--gray-700);
  background: #F8FAFC;
}
.category-box-grid a i {
  color: var(--green);
  width: 16px;
  text-align: center;
}

/* Jobs grid page */
.jobs-page-head {
  max-width: 1180px;
  margin: 0 auto;
  padding: 24px 24px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.jobs-page-head h1 {
  font-size: 20px;
  font-weight: 800;
}
.jobs-grid {
  max-width: 1180px;
  margin: 0 auto;
  padding: 0 24px 32px;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14px;
}
.job-grid-card {
  background: white;
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.job-grid-card .job-grid-store {
  font-size: 13px;
  font-weight: 800;
  color: var(--ink);
}
.job-grid-card .job-grid-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--gray-700);
  line-height: 1.4;
  min-height: 36px;
}
.job-grid-card .job-grid-meta {
  font-size: 11px;
  color: var(--gray-400);
}
.job-grid-card .job-grid-wage {
  font-size: 13px;
  font-weight: 800;
  color: var(--green-dark);
}
.job-grid-card .btn-sm {
  margin-top: 6px;
}

@media (max-width: 980px) {
  .home-hero-grid {
    grid-template-columns: 1fr;
  }
  .home-category-grid {
    grid-template-columns: 1fr;
  }
  .jobs-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
  .site-search {
    order: 3;
    flex-basis: 100%;
    max-width: none;
  }
}
@media (max-width: 640px) {
  .jobs-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .category-box-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
```

- [ ] **Step 2: 빌드(정적 리소스 복사) 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (CSS는 컴파일 대상이 아니지만, 프로젝트 빌드가
깨지지 않는지 확인하는 용도)

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/static/css/style.css
git commit -m "style: 알바천국 스타일 헤더/히어로/카테고리/잡그리드 CSS 추가"
```

---

### Task 3: `siteHeader` 공통 프래그먼트 추가

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`

- [ ] **Step 1: `layout.html`에 `siteHeader` 프래그먼트 추가**

`src/main/resources/templates/fragments/layout.html`의 `<div th:fragment="scripts">`
블록(38번째 줄) **바로 앞**에 아래 프래그먼트를 추가한다. 이 프래그먼트는
호출하는 페이지의 모델 변수(`member`, `activeRole`)를 그대로 참조한다.

```html
<header th:fragment="siteHeader" class="site-header">
  <div class="site-header-top">
    <a class="logo-mark" th:href="@{/}">
      <span class="logo-icon"><i class="fa-solid fa-bolt"></i></span>
      <span class="logo-text">바로<span>알바</span></span>
    </a>
    <form class="site-search" th:action="@{/worker/home}" method="get">
      <label class="search-field">
        <i class="fa-solid fa-magnifying-glass"></i>
        <input name="keyword" placeholder="알바, 지역, 업종을 검색하세요">
      </label>
      <input class="filter-input" name="region" placeholder="지역">
      <input class="filter-input" name="category" placeholder="업종">
      <button class="btn btn-primary btn-sm" type="submit">검색</button>
    </form>
    <div class="site-header-actions">
      <a th:if="${member == null}" th:href="@{/signup}">회원가입</a>
      <a th:if="${member == null}" th:href="@{/}">로그인</a>
      <a th:if="${member != null}" class="mode-switch-link" th:href="@{/role}">
        <i class="fa-solid fa-repeat"></i>
        모드 전환
      </a>
      <a th:if="${member != null}" class="avatar avatar-48 avatar-worker" th:href="@{/mypage}" aria-label="마이페이지">
        <i class="fa-regular fa-user"></i>
      </a>
    </div>
  </div>
  <nav class="site-gnb" aria-label="주요 메뉴">
    <div class="site-gnb-menu">
      <a th:href="@{/worker/home}">채용정보</a>
      <a th:href="@{/boss/home}">인재정보</a>
      <a th:href="@{/worker/home(region='서울특별시')}">지역별</a>
      <a th:href="@{/worker/home(category='외식·음료')}">업종별</a>
      <a th:href="@{/eval}">평가관리</a>
      <a th:href="@{/role}">양방향 매칭</a>
    </div>
    <div class="site-gnb-actions">
      <a class="gnb-btn gnb-btn-worker" th:href="@{/worker/profile/edit}">구직 프로필 등록</a>
      <a class="gnb-btn gnb-btn-boss" th:href="@{/boss/recruitments/new}">공고 등록</a>
    </div>
  </nav>
</header>

```

- [ ] **Step 2: 전체 테스트 실행 (프래그먼트 문법 오류 여부 확인)**

Run: `./gradlew test --tests "project.jjb.JjbMvcPageTests"`
Expected: PASS (이 시점에서는 아직 어떤 페이지도 `siteHeader`를 사용하지
않으므로 기존 테스트는 그대로 통과해야 한다)

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/templates/fragments/layout.html
git commit -m "feat: 데스크톱 사이트 헤더/GNB 공통 프래그먼트(siteHeader) 추가"
```

---

### Task 4: 홈 화면(`index.html`) 전면 개편

**Files:**
- Modify: `src/main/resources/templates/index.html`
- Test: `src/test/java/project/jjb/JjbMvcPageTests.java` (기존 `mainMvcPagesRender`)

- [ ] **Step 1: `index.html` 전체 교체**

`src/main/resources/templates/index.html` 전체를 아래 내용으로 교체한다.
기존 로그인 폼/소셜 로그인 버튼 마크업은 그대로 유지하여 OAuth 링크,
`/login/local`, `/signup` 등 기존 테스트가 검증하는 요소를 보존한다.

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('시작')}"></head>
<body>
<main class="app-shell job-portal-shell public-portal">
  <section class="screen active no-bottom">
    <div th:replace="~{fragments/layout :: siteHeader}"></div>

    <div class="home-hero-grid">
      <article class="hero-jobs-card">
        <h2>
          실시간 등록 공고
          <a th:href="@{/jobs}">더보기</a>
        </h2>
        <div class="empty-state" th:if="${#lists.isEmpty(recentJobs)}">
          아직 등록된 공고가 없습니다.
        </div>
        <a class="hero-job-item" th:each="job : ${recentJobs}" th:href="@{/jobs}">
          <strong th:text="${job.storeName}">매장명</strong>
          <span th:text="${job.title}">공고 제목</span>
          <span th:text="${job.workTime} + ' · ' + ${job.workplaceAddress}">근무시간 · 주소</span>
          <span class="hero-job-wage" th:text="${#numbers.formatInteger(job.hourlyWage, 0, 'COMMA')} + '원/시간'">시급</span>
        </a>
      </article>

      <article class="hero-banner-card">
        <p class="eyebrow">구인구직 포털 + 양방향 매칭</p>
        <h1 class="login-headline">급할 때 바로 매칭,<br>믿을 수 있는 단기 알바</h1>
        <p class="login-sub">구직자는 가게에 지원하고, 인증 사장님은 좋은 구직자에게 직접 제안합니다.</p>
        <a class="btn btn-primary btn-sm" th:href="@{/role}">양방향 매칭 시작하기</a>
      </article>

      <aside class="public-login-card">
        <p class="page-message error" th:if="${errorMessage}" th:text="${errorMessage}"></p>
        <form class="local-auth-form" th:action="@{/login/local}" method="post">
          <div class="form-group">
            <label class="form-label" for="loginUsername">아이디</label>
            <input class="form-input" id="loginUsername" name="username" autocomplete="username" placeholder="아이디" required>
          </div>
          <div class="form-group">
            <label class="form-label" for="loginPassword">비밀번호</label>
            <input class="form-input" id="loginPassword" name="password" type="password" autocomplete="current-password" minlength="8" required>
          </div>
          <button class="btn btn-primary" type="submit">
            <i class="fa-regular fa-user"></i>
            아이디로 로그인
          </button>
        </form>
        <a class="btn btn-secondary" th:href="@{/signup}">아이디로 회원가입</a>
        <div class="login-divider"><span>소셜 로그인</span></div>
        <a class="social-btn social-btn-kakao" th:href="@{/oauth2/authorization/kakao}">
          <span class="social-btn-icon">K</span>
          <span class="social-btn-label">카카오로 시작하기</span>
        </a>
        <a class="social-btn social-btn-naver" th:href="@{/oauth2/authorization/naver}">
          <span class="social-btn-icon">N</span>
          <span class="social-btn-label">네이버로 시작하기</span>
        </a>
      </aside>
    </div>

    <div class="home-category-grid">
      <section class="category-box">
        <h3>지역·동네 알바</h3>
        <div class="category-box-grid">
          <a th:href="@{/worker/home(region='서울특별시')}"><i class="fa-solid fa-location-dot"></i> 서울</a>
          <a th:href="@{/worker/home(region='경기도')}"><i class="fa-solid fa-location-dot"></i> 경기</a>
          <a th:href="@{/worker/home(region='인천광역시')}"><i class="fa-solid fa-location-dot"></i> 인천</a>
          <a th:href="@{/worker/home(region='부산광역시')}"><i class="fa-solid fa-location-dot"></i> 부산</a>
          <a th:href="@{/worker/home(region='대구광역시')}"><i class="fa-solid fa-location-dot"></i> 대구</a>
          <a th:href="@{/worker/home(region='대전광역시')}"><i class="fa-solid fa-location-dot"></i> 대전</a>
        </div>
      </section>

      <section class="category-box">
        <h3>대상별 알바</h3>
        <div class="category-box-grid">
          <a th:href="@{/worker/home(keyword='대학생')}"><i class="fa-solid fa-graduation-cap"></i> 대학생</a>
          <a th:href="@{/worker/home(keyword='주부')}"><i class="fa-solid fa-house"></i> 주부</a>
          <a th:href="@{/worker/home(keyword='청소년')}"><i class="fa-solid fa-child-reaching"></i> 청소년</a>
          <a th:href="@{/worker/home(keyword='외국인')}"><i class="fa-solid fa-earth-asia"></i> 외국인</a>
          <a th:href="@{/worker/home(keyword='시니어')}"><i class="fa-solid fa-user-tie"></i> 시니어</a>
          <a th:href="@{/worker/home(category='카페')}"><i class="fa-solid fa-mug-hot"></i> 카페</a>
        </div>
      </section>

      <section class="category-box">
        <h3>양방향 매칭 가이드</h3>
        <div class="category-box-grid">
          <a th:href="@{/worker/home}"><i class="fa-solid fa-paper-plane"></i> 지원현황</a>
          <a th:href="@{/boss/home}"><i class="fa-solid fa-handshake"></i> 받은 제안</a>
          <a th:href="@{/eval}"><i class="fa-solid fa-star"></i> 평가관리</a>
          <a th:href="@{/boss/recruitments/new}"><i class="fa-solid fa-bullhorn"></i> 공고등록</a>
        </div>
      </section>
    </div>

    <p class="terms-copy">가입하면 서비스 이용약관 및 개인정보처리방침에 동의합니다.</p>
  </section>
</main>
<div th:replace="~{fragments/layout :: scripts}"></div>
</body>
</html>
```

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew test`
Expected: PASS — 특히 `mainMvcPagesRender`(`"급할 때 바로 매칭"`,
`/login/local`, `/signup`, kakao/naver OAuth 링크, `/css/style.css`,
`/js/app.js` 포함 여부)와 `jobsPageListsOpenRecruitments`(`"실시간 등록
공고"`, `"잡스카페"` 포함 여부)가 모두 통과해야 한다.

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/templates/index.html
git commit -m "feat: 홈 화면을 알바천국 스타일 3단 히어로 + 카테고리 그리드로 개편"
```

---

### Task 5: `jobs.html` 카드 그리드 완성

**Files:**
- Modify: `src/main/resources/templates/jobs.html`

- [ ] **Step 1: `jobs.html`을 카드 그리드 레이아웃으로 교체**

`src/main/resources/templates/jobs.html`(Task 1의 placeholder)을 아래
내용으로 교체한다.

```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head th:replace="~{fragments/layout :: head('실시간 공고')}"></head>
<body>
<main class="app-shell job-portal-shell public-portal">
  <section class="screen active no-bottom">
    <div th:replace="~{fragments/layout :: siteHeader}"></div>

    <div class="jobs-page-head">
      <h1>실시간 등록 공고</h1>
      <span class="section-sub" th:text="${#lists.size(jobCards)} + '개의 공고'">0개의 공고</span>
    </div>

    <div class="empty-state" th:if="${#lists.isEmpty(jobCards)}">
      아직 등록된 공고가 없습니다.
    </div>

    <div class="jobs-grid" th:unless="${#lists.isEmpty(jobCards)}">
      <article class="job-grid-card" th:each="job : ${jobCards}">
        <span class="tag-chip" th:text="${job.businessCategory}">업종</span>
        <strong class="job-grid-store" th:text="${job.storeName}">매장명</strong>
        <p class="job-grid-title" th:text="${job.title}">공고 제목</p>
        <span class="job-grid-meta" th:text="${job.workTime}">근무시간</span>
        <span class="job-grid-meta" th:text="${job.workplaceAddress}">주소</span>
        <span class="job-grid-wage" th:text="${#numbers.formatInteger(job.hourlyWage, 0, 'COMMA')} + '원/시간'">시급</span>
        <a class="btn btn-outline btn-sm" th:href="@{/worker/home}">상세보기</a>
      </article>
    </div>
  </section>
</main>
<div th:replace="~{fragments/layout :: scripts}"></div>
</body>
</html>
```

- [ ] **Step 2: 전체 테스트 실행**

Run: `./gradlew test`
Expected: PASS

- [ ] **Step 3: 커밋**

```bash
git add src/main/resources/templates/jobs.html
git commit -m "style: 실시간 공고 전체보기 페이지를 카드 그리드 레이아웃으로 완성"
```

---

### Task 6: 최종 점검 (애플리케이션 부팅 + 수동 확인)

**Files:** 없음 (검증 전용)

- [ ] **Step 1: 전체 테스트 스위트 실행**

Run: `./gradlew test`
Expected: BUILD SUCCESSFUL, 모든 테스트 PASS

- [ ] **Step 2: 애플리케이션 부팅 후 수동 확인**

Run: `./gradlew bootRun` (백그라운드 실행 후 별도 터미널에서 확인)

```bash
curl -s http://localhost:8080/ | grep -c "site-header"
curl -s http://localhost:8080/jobs | grep -c "jobs-grid"
```

Expected: 두 명령 모두 `1` 이상 출력 (해당 클래스가 렌더링됨)

- [ ] **Step 3: 부팅 프로세스 종료**

Run: `./gradlew --stop` 또는 `bootRun`을 실행한 백그라운드 프로세스 종료

---

## Self-Review Notes

- **스펙 커버리지**: 1) 공통 헤더/GNB → Task 3, 4 / 2) 홈 3단 히어로 +
  카테고리 그리드 → Task 4 / 3) `/jobs` 카드 그리드 → Task 1, 5 / 4) CSS →
  Task 2. 모두 매핑됨.
- **타입 일관성**: `JobListingCard(id, title, storeName, businessCategory,
  workplaceAddress, workTime, hourlyWage, status)` 필드명을 `jobs.html`,
  `index.html`에서 동일하게 사용 (`job.storeName`, `job.title`,
  `job.workTime`, `job.workplaceAddress`, `job.hourlyWage`,
  `job.businessCategory`).
- **비범위 확인**: 브랜드 로고 캐러셀, 외부 광고 배너, worker_home/boss_home
  등 다른 페이지의 데스크톱 전환은 포함하지 않음 (다음 단계).
