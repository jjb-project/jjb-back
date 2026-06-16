# 토스 스타일 회원가입 + 홈 화이트 톤 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 회원가입 페이지(`signup.html`)를 토스 스타일(화이트 배경, 큰 타이포그래피,
보더리스 인풋, 하단 고정 CTA)로 리디자인하고, 홈 화면(`index.html`)의 배경을
화이트로 바꾸고 GNB의 광고성 액션 버튼 2개를 제거한다.

**Architecture:** 기존 구조/JS 로직은 그대로 두고, CSS에 새 스코프 클래스
(`.signup-screen`, `.signup-form`)와 `.public-portal` 카드 오버라이드를
추가한다. `signup.html`은 클래스 추가 + 중복 버튼 1개 제거만 한다.
`fragments/layout.html`의 공용 `siteHeader`에서 GNB 액션 버튼 div와 관련 CSS를
제거한다.

**Tech Stack:** Spring Boot + Thymeleaf, 순수 CSS (`src/main/resources/static/css/style.css`)

---

## 참고 문서

- 설계 문서: `docs/superpowers/specs/2026-06-13-toss-style-signup-white-home-design.md`

## 검증 방식

이 작업은 CSS/HTML 변경이며 자동화된 단위 테스트가 없다. 각 작업은
`grep`으로 변경 전/후 결과를 확인하는 방식으로 검증한다 (TDD의 "테스트"를
구조적 검증으로 대체). 모든 작업이 끝난 뒤, 가능하면 `/run` 스킬로 앱을
띄워 `/`, `/signup` 페이지를 브라우저에서 육안 확인한다 (DB 연결이 필요해
선택 사항).

---

### Task 1: 회원가입 템플릿에 토스 스타일 스코프 클래스 추가 + 중복 버튼 제거

**Files:**
- Modify: `src/main/resources/templates/signup.html`

- [ ] **Step 1: `<section>`에 `signup-screen` 클래스 추가**

`src/main/resources/templates/signup.html`에서:

```html
<main class="app-shell">
  <section class="screen active">
```

위를 아래로 변경:

```html
<main class="app-shell">
  <section class="screen active signup-screen">
```

- [ ] **Step 2: `<form>`에 `signup-form` 클래스 추가**

같은 파일에서:

```html
    <form class="form-screen" th:action="@{/signup/local}" method="post" data-signup-form>
```

위를 아래로 변경:

```html
    <form class="form-screen signup-form" th:action="@{/signup/local}" method="post" data-signup-form>
```

- [ ] **Step 3: "로그인으로 돌아가기" 버튼 제거**

같은 파일에서, 제출 버튼 바로 다음에 있는 아래 줄을 삭제한다:

```html

      <a class="btn btn-secondary" th:href="@{/}">로그인으로 돌아가기</a>
```

즉, 아래:

```html
      <button class="btn btn-primary" type="submit" data-signup-submit disabled>
        <i class="fa-solid fa-user-plus"></i>
        계정 만들기
      </button>

      <a class="btn btn-secondary" th:href="@{/}">로그인으로 돌아가기</a>
    </form>
```

를 아래로 변경:

```html
      <button class="btn btn-primary" type="submit" data-signup-submit disabled>
        <i class="fa-solid fa-user-plus"></i>
        계정 만들기
      </button>
    </form>
```

- [ ] **Step 4: 변경 확인**

Run: `grep -n "signup-screen\|signup-form\|로그인으로 돌아가기" src/main/resources/templates/signup.html`

Expected:
- `signup-screen`, `signup-form` 문자열이 각각 한 번씩 나타남
- `로그인으로 돌아가기`는 결과에 나타나지 않음 (출력 없음)

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/templates/signup.html
git commit -m "feat: 회원가입 화면에 토스 스타일 스코프 클래스 추가 및 중복 버튼 제거"
```

---

### Task 2: 회원가입 화면 토스 스타일 CSS 추가

**Files:**
- Modify: `src/main/resources/static/css/style.css` (파일 끝, 1317번째 줄 뒤에 추가)

- [ ] **Step 1: 파일 끝 줄 수 확인**

Run: `wc -l src/main/resources/static/css/style.css`

Expected: `1317 src/main/resources/static/css/style.css`

- [ ] **Step 2: 토스 스타일 CSS 블록 추가**

`src/main/resources/static/css/style.css` 파일 맨 끝(1317번째 줄, `@media (max-width: 640px) { ... }`의 마지막 `}` 다음)에 아래 블록을 추가한다.

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

- [ ] **Step 3: 변경 확인**

Run: `grep -n "signup-screen\|signup-form\|data-signup-submit" src/main/resources/static/css/style.css`

Expected: 위에서 추가한 셀렉터들이 모두 출력됨 (`.signup-screen`, `.signup-form ...`, `.signup-form [data-signup-submit]` 포함)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/static/css/style.css
git commit -m "style: 회원가입 화면 토스 스타일(화이트 배경/큰 타이포/보더리스 인풋/하단 고정 CTA) 적용"
```

---

### Task 3: 홈 화면(공개 포털) 화이트 톤 CSS 추가

**Files:**
- Modify: `src/main/resources/static/css/style.css` (Task 2에서 추가한 블록 뒤에 이어서 추가)

- [ ] **Step 1: 화이트 톤 CSS 블록 추가**

`src/main/resources/static/css/style.css` 파일 맨 끝(Task 2에서 추가한 `.signup-form [data-signup-submit] { ... }` 블록 다음)에 아래 블록을 추가한다.

```css

/* ===== Public portal white tone ===== */
.job-portal-shell.public-portal {
  background: #FFFFFF;
}
.public-portal .hero-jobs-card,
.public-portal .public-login-card,
.public-portal .category-box {
  background: #F8F9FA;
  border: none;
}
```

- [ ] **Step 2: 변경 확인**

Run: `grep -n "job-portal-shell.public-portal\|public-portal .hero-jobs-card" src/main/resources/static/css/style.css`

Expected: 위에서 추가한 두 셀렉터 블록이 출력됨

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/static/css/style.css
git commit -m "style: 홈 화면(공개 포털) 배경과 카드 톤을 화이트 기반으로 변경"
```

---

### Task 4: GNB 광고성 액션 버튼 제거

**Files:**
- Modify: `src/main/resources/templates/fragments/layout.html`
- Modify: `src/main/resources/static/css/style.css`

- [ ] **Step 1: `siteHeader` 프래그먼트에서 액션 버튼 div 제거**

`src/main/resources/templates/fragments/layout.html`에서 아래 블록을 찾는다:

```html
    <div class="site-gnb-actions">
      <a class="gnb-btn gnb-btn-worker" th:href="@{/worker/profile/edit}">구직 프로필 등록</a>
      <a class="gnb-btn gnb-btn-boss" th:href="@{/boss/recruitments/new}">공고 등록</a>
    </div>
```

이 블록 전체를 삭제한다. 즉, 아래:

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

를 아래로 변경:

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

- [ ] **Step 2: 관련 CSS 규칙 제거**

`src/main/resources/static/css/style.css`에서 아래 블록을 찾아 전체 삭제한다:

```css
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

Run: `grep -rn "site-gnb-actions\|gnb-btn" src/main/resources/templates/fragments/layout.html src/main/resources/static/css/style.css`

Expected: 출력 없음 (아무 결과도 나오지 않아야 함)

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/templates/fragments/layout.html src/main/resources/static/css/style.css
git commit -m "refactor: 홈 GNB의 구직 프로필 등록/공고 등록 버튼과 관련 CSS 제거"
```

---

## 최종 점검 (선택)

- [ ] `mvn -q -DskipTests compile` (또는 `./gradlew compileJava`) 로 Thymeleaf
  템플릿/리소스가 빌드에 포함되는지 확인 (선택, 컴파일 자체는 템플릿 문법을
  검증하지 않지만 빌드가 깨지지 않는지 확인하는 차원)
- [ ] 가능하면 `/run` 스킬로 앱을 띄우고 `/`, `/signup` 페이지를 브라우저로
  확인: 회원가입 화면이 화이트 배경 + 큰 인풋 + 하단 고정 CTA로 보이는지,
  홈 화면 배경이 화이트이고 GNB에 색깔 버튼 2개가 사라졌는지 확인
