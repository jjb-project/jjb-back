# 공개 포털 화이트 톤 정리 + 와이드 컨테이너 + 회원가입 디자인 통합 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 데스크톱 공개 포털(`/`, `/jobs`, `/signup`) 컨테이너 폭을 1180px →
1440px로 넓히고, GNB의 핵심 기능 버튼("구직 프로필 등록"/"공고 등록")을
복원하고, "양방향 매칭" 배너를 광고 배너처럼 보이지 않도록 화이트 톤으로
정리하고, 회원가입 페이지를 홈/`/jobs`와 동일한 `siteHeader` +
`job-portal-shell` 디자인 시스템으로 통합한다.

**Architecture:** 순수 CSS 폭/색상 값 수정과 `fragments/layout.html`,
`signup.html`의 마크업 구조 변경만 진행한다. 새 컴포넌트(`.signup-page`,
`.signup-card`)는 기존 `.public-login-card`/`.local-auth-form` 패턴을
재사용한다.

**Tech Stack:** Spring Boot + Thymeleaf, 순수 CSS (`src/main/resources/static/css/style.css`)

---

## 참고 문서

- 설계 문서: `docs/superpowers/specs/2026-06-15-public-portal-cleanup-and-signup-unify-design.md`

## 검증 방식

CSS/HTML 변경이며 자동화된 단위 테스트가 없다. 각 작업은 `grep`으로 변경
전/후 결과를 확인하는 방식으로 검증한다. 모든 작업이 끝난 뒤, 가능하면
`/run` 스킬로 앱을 띄워 `/`, `/jobs`, `/signup` 페이지를 브라우저에서
육안 확인한다 (DB 연결이 필요해 선택 사항).

---

### Task 1: 공개 포털 컨테이너 와이드화 (1180px → 1440px)

**Files:**
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: 콜론 뒤 공백 없는 `max-width:1180px;` 일괄 변경**

`src/main/resources/static/css/style.css`에서 `max-width:1180px;`
(콜론 뒤 공백 없음, 623/853/986번째 줄 — `.job-portal-shell` 기본 규칙,
`@media (min-width: 760px)` 내부 `.job-portal-shell`, `.job-portal-shell ~
.bottom-nav`)을 모두 `max-width:1440px;`로 변경한다 (3곳, `replace_all`).

- [ ] **Step 2: 콜론 뒤 공백 있는 `max-width: 1180px;` 일괄 변경**

같은 파일에서 `max-width: 1180px;` (콜론 뒤 공백 있음, 996/1066/1091/1179/1222/1234
번째 줄 — `.site-header-top`, `.site-gnb`, `.home-hero-grid`,
`.home-category-grid`, `.jobs-page-head`, `.jobs-grid`)을 모두
`max-width: 1440px;`로 변경한다 (6곳, `replace_all`).

- [ ] **Step 3: 변경 확인**

Run: `grep -c "1180px" src/main/resources/static/css/style.css && grep -c "1440px" src/main/resources/static/css/style.css`

Expected: 첫 번째 결과 `0`, 두 번째 결과 `9`

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/style.css
git commit -m "style: 공개 포털 컨테이너 폭을 1180px에서 1440px로 확대"
```

---

### Task 2: GNB 핵심 기능 버튼("구직 프로필 등록"/"공고 등록") 복원

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: `siteHeader` 프래그먼트의 `site-gnb`에 액션 버튼 div 추가**

`src/main/resources/templates/fragments/layout.html`에서 아래 블록을 찾는다:

```html
  <nav class="site-gnb" aria-label="주요 메뉴">
    <div class="site-gnb-menu">
      <a th:href="@{/worker/home}">채용정보</a>
      <a th:href="@{/boss/home}">인재정보</a>
      <a th:href="@{/worker/home(region='서울특별시')}">지역별</a>
      <a th:href="@{/worker/home(category='외식·음료')}">업종별</a>
      <a th:href="@{/eval}">평가관리</a>
      <a th:href="@{/role}">양방향 매칭</a>
    </div>
  </nav>
```

아래로 변경 (`site-gnb-menu` div 다음에 `site-gnb-actions` div 추가):

```html
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
```

- [ ] **Step 2: `site-gnb-actions`/`gnb-btn` CSS 복원**

`src/main/resources/static/css/style.css`에서 아래 블록을 찾는다:

```css
.site-gnb-menu a:last-child {
  color: var(--green-mid);
}
```

바로 다음에 아래 블록을 추가한다:

```css
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
```

- [ ] **Step 3: 변경 확인**

Run: `grep -n "site-gnb-actions\|gnb-btn" src/main/resources/templates/fragments/layout.html src/main/resources/static/css/style.css`

Expected: `layout.html`에 `site-gnb-actions`, `gnb-btn-worker`, `gnb-btn-boss`가
포함된 줄 출력, `style.css`에 `.site-gnb-actions`, `.gnb-btn`,
`.gnb-btn-worker`, `.gnb-btn-boss` 선택자가 출력됨

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html src/main/resources/static/css/style.css
git commit -m "feat: GNB에 구직 프로필 등록/공고 등록 버튼 복원"
```

---

### Task 3: "양방향 매칭" 배너(`.hero-banner-card`) 화이트 톤 정리

**Files:**
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: `.hero-banner-card` 색상 오버라이드를 화이트 톤으로 변경**

`src/main/resources/static/css/style.css`에서 아래 블록을 찾는다:

```css
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
```

아래로 변경:

```css
.hero-banner-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 10px;
  text-align: left;
}
.hero-banner-card .eyebrow {
  font-size: 12px;
  font-weight: 800;
  color: var(--green);
}
.hero-banner-card h1 {
  font-size: 22px;
  font-weight: 800;
  line-height: 1.35;
  color: var(--ink);
}
.hero-banner-card p {
  font-size: 13px;
  line-height: 1.6;
  color: var(--gray-600);
}
```

(이 블록 바로 다음에 있는 `.hero-banner-card .btn { width: auto; align-self:
flex-start; }`는 변경하지 않는다.)

- [ ] **Step 2: 화이트 톤 셀렉터 그룹에 `.hero-banner-card` 추가**

같은 파일에서 아래 블록을 찾는다:

```css
.public-portal .hero-jobs-card,
.public-portal .public-login-card,
.public-portal .category-box {
  background: #F8F9FA;
  border: none;
}
```

아래로 변경:

```css
.public-portal .hero-jobs-card,
.public-portal .hero-banner-card,
.public-portal .public-login-card,
.public-portal .category-box {
  background: #F8F9FA;
  border: none;
}
```

- [ ] **Step 3: 변경 확인**

Run: `grep -A1 "^\.hero-banner-card {$" src/main/resources/static/css/style.css`

Expected: `.hero-banner-card {` 다음 줄이 `  display: flex;`로 출력됨
(기존 `  background: linear-gradient(...)`가 아님 — `.login-hero`는 다른
선택자라 영향 없음)

Run: `grep -n "hero-banner-card" src/main/resources/static/css/style.css`

Expected: `.hero-banner-card`, `.hero-banner-card .eyebrow`,
`.hero-banner-card h1`, `.hero-banner-card p`, `.hero-banner-card .btn`,
`.public-portal .hero-banner-card`(그룹 셀렉터 내) 가 모두 출력됨

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/style.css
git commit -m "style: 양방향 매칭 배너를 화이트 톤으로 정리"
```

---

### Task 4: 회원가입 페이지를 홈과 동일한 디자인 시스템으로 통합

**Files:**
- Modify: `src/main/resources/templates/signup.html`
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: `signup.html` 구조를 `siteHeader` + `.signup-card`로 변경**

`src/main/resources/templates/signup.html`에서 `<main>`부터 `</main>`까지
아래 블록을 찾는다:

```html
<main class="app-shell">
  <section class="screen active signup-screen">
    <div th:replace="~{fragments/layout :: topbar('아이디 회원가입', '/')}"></div>

    <form class="form-screen signup-form" th:action="@{/signup/local}" method="post" data-signup-form>
      <div class="page-intro">
        <h1>아이디로 계정을 만들어요</h1>
        <p>가입 후 바로 이용 목적을 선택하고 필요한 프로필을 등록할 수 있습니다.</p>
      </div>

      <p class="page-message error" th:if="${errorMessage}" th:text="${errorMessage}"></p>

      <div class="form-group">
        <label class="form-label" for="signupDisplayName">이름</label>
        <input class="form-input" id="signupDisplayName" name="displayName" autocomplete="name" placeholder="홍길동" required>
      </div>

      <div class="form-group">
        <label class="form-label" for="signupUsername">아이디</label>
        <div class="email-check-row">
          <input class="form-input" id="signupUsername" name="username" autocomplete="username"
                 placeholder="영문/숫자 4자 이상" pattern="[A-Za-z0-9._-]{4,40}" required data-username-input>
          <button class="btn btn-secondary email-check-btn" type="button" data-username-check-button>중복확인</button>
        </div>
        <p class="form-help" data-username-check-message role="status" aria-live="polite"></p>
      </div>

      <div class="form-group">
        <label class="form-label" for="signupPassword">비밀번호</label>
        <input class="form-input" id="signupPassword" name="password" type="password" autocomplete="new-password" minlength="8" required>
      </div>

      <button class="btn btn-primary" type="submit" data-signup-submit disabled>
        <i class="fa-solid fa-user-plus"></i>
        계정 만들기
      </button>
    </form>
  </section>
</main>
```

아래로 변경:

```html
<main class="app-shell job-portal-shell public-portal">
  <section class="screen active no-bottom">
    <div th:replace="~{fragments/layout :: siteHeader}"></div>

    <div class="signup-page">
      <div class="signup-card">
        <div class="page-intro">
          <h1>아이디로 계정을 만들어요</h1>
          <p>가입 후 바로 이용 목적을 선택하고 필요한 프로필을 등록할 수 있습니다.</p>
        </div>

        <p class="page-message error" th:if="${errorMessage}" th:text="${errorMessage}"></p>

        <form class="local-auth-form" th:action="@{/signup/local}" method="post" data-signup-form>
          <div class="form-group">
            <label class="form-label" for="signupDisplayName">이름</label>
            <input class="form-input" id="signupDisplayName" name="displayName" autocomplete="name" placeholder="홍길동" required>
          </div>

          <div class="form-group">
            <label class="form-label" for="signupUsername">아이디</label>
            <div class="email-check-row">
              <input class="form-input" id="signupUsername" name="username" autocomplete="username"
                     placeholder="영문/숫자 4자 이상" pattern="[A-Za-z0-9._-]{4,40}" required data-username-input>
              <button class="btn btn-secondary email-check-btn" type="button" data-username-check-button>중복확인</button>
            </div>
            <p class="form-help" data-username-check-message role="status" aria-live="polite"></p>
          </div>

          <div class="form-group">
            <label class="form-label" for="signupPassword">비밀번호</label>
            <input class="form-input" id="signupPassword" name="password" type="password" autocomplete="new-password" minlength="8" required>
          </div>

          <button class="btn btn-primary" type="submit" data-signup-submit disabled>
            <i class="fa-solid fa-user-plus"></i>
            계정 만들기
          </button>
        </form>
      </div>
    </div>
  </section>
</main>
```

- [ ] **Step 2: 변경 확인**

Run: `grep -n "signup-screen\|signup-form\|form-screen\|siteHeader\|signup-page\|signup-card\|local-auth-form" src/main/resources/templates/signup.html`

Expected: `signup-screen`, `signup-form`, `form-screen`은 결과에 나타나지
않음. `siteHeader`, `signup-page`, `signup-card`, `local-auth-form`이
포함된 줄이 각각 출력됨.

- [ ] **Step 3: 토스 스타일 CSS 블록을 `.signup-page`/`.signup-card`로 교체**

`src/main/resources/static/css/style.css`에서 아래 블록을 찾는다:

```css
/* ===== Signup (Toss-style) ===== */
.signup-screen {
  background: #FFFFFF;
}
.signup-form {
  padding: 0 20px 100px;
}
.signup-form .page-intro h1 {
  font-size: 28px;
  font-weight: 800;
  line-height: 1.4;
}
.signup-form .page-intro p {
  font-size: 14px;
  color: var(--gray-400);
  line-height: 1.6;
}
.signup-form .form-label {
  font-size: 13px;
  color: var(--gray-400);
  font-weight: 600;
}
.signup-form .form-input {
  height: 56px;
  padding: 0 16px;
  border: 2px solid transparent;
  border-radius: 12px;
  background: #F2F4F6;
  font-size: 16px;
}
.signup-form .form-input:focus {
  background: #FFFFFF;
  border-color: var(--blue);
}
.signup-form .email-check-btn {
  height: 56px;
  border-radius: 12px;
  background: #F2F4F6;
  color: var(--gray-600);
}
.signup-form [data-signup-submit] {
  position: sticky;
  bottom: 16px;
  height: 56px;
  border-radius: 12px;
  font-size: 16px;
  box-shadow: 0 4px 16px rgba(37, 99, 235, 0.25);
}
```

아래로 변경:

```css
/* ===== Signup page ===== */
.signup-page {
  display: flex;
  justify-content: center;
  padding: 40px 24px 64px;
}
.signup-card {
  width: 100%;
  max-width: 480px;
  background: #F8F9FA;
  border: 1px solid var(--gray-200);
  border-radius: 8px;
  padding: 24px;
}
.signup-card .local-auth-form {
  padding: 0;
  border: 0;
}
```

- [ ] **Step 4: 변경 확인**

Run: `grep -n "signup-screen\|signup-form\|signup-page\|signup-card" src/main/resources/static/css/style.css`

Expected: `signup-screen`, `signup-form`은 결과에 나타나지 않음.
`.signup-page`, `.signup-card`, `.signup-card .local-auth-form`이 출력됨.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/signup.html src/main/resources/static/css/style.css
git commit -m "feat: 회원가입 페이지를 홈과 동일한 디자인 시스템(siteHeader + signup-card)으로 통합"
```

---

## 최종 점검 (선택)

- [ ] `mvn -q -DskipTests compile` (또는 `./gradlew compileJava`)로 빌드가
  깨지지 않는지 확인 (Thymeleaf 문법 자체는 검증하지 않지만 리소스 포함
  여부 확인)
- [ ] 가능하면 `/run` 스킬로 앱을 띄우고 브라우저로 확인:
  - `/`: 화면이 큰 모니터에서 더 넓게 채워지는지, GNB 우측에 "구직 프로필
    등록"(초록)/"공고 등록"(파랑) 버튼이 보이는지, "양방향 매칭" 카드가
    다른 카드와 같은 화이트 톤(녹색 그라데이션이 사라짐)인지 확인
  - `/jobs`: 카드 그리드가 더 넓어졌는지 확인
  - `/signup`: 홈과 동일한 헤더+GNB가 보이고, 회원가입 폼이 흰 카드
    안에 중앙 정렬되어 있으며 인풋/버튼 모양이 홈의 로그인 폼과
    동일한지 확인
