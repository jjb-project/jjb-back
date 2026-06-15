# 공개 포털 화이트 톤 정리 + 와이드 컨테이너 + 회원가입 디자인 시스템 통합

## 배경

`2026-06-13-toss-style-signup-white-home-design.md`에서 회원가입 화면을
토스 스타일(화이트 배경/큰 타이포/보더리스 인풋/하단 고정 CTA)로 별도
디자인하고, GNB의 "구직 프로필 등록"/"공고 등록" 버튼을 광고성 요소로
판단해 제거했다.

그러나 원래 요청의 취지는 "알바천국 페이지를 모티브로 하다 보니 함께
들어온 **광고성 요소**(K-HIRE/DAEKYO 외부 광고 배너, 브랜드 로고
캐러셀)를 빼달라"는 것이었고, 이는 `2026-06-12-albamon-style-home-redesign.md`
스펙에서 이미 제외 대상으로 명시되어 실제로 구현된 적이 없다. 반면
"구직 프로필 등록/공고 등록"은 알바천국의 "이력서 등록/공고 등록"과
동일한 **핵심 기능 진입점**이며 광고가 아니다.

이번 작업은:

1. 회원가입을 별도 "토스 스타일"로 떼어내는 대신, 홈/`/jobs`와 **같은
   디자인 시스템(화이트 톤 + `siteHeader` + `job-portal-shell`)** 안에서
   통합한다.
2. 데스크톱 웹 화면을 더 꽉 채우도록 컨테이너 폭을 넓힌다.
3. 53065b5에서 제거한 GNB 핵심 기능 버튼을 복원한다.
4. "양방향 매칭" 배너(`.hero-banner-card`)의 초록 그라데이션을 화이트
   톤으로 정리해 광고 배너처럼 보이는 부분을 없앤다.

## 범위

### 1. 컨테이너 와이드화 (1180px → 1440px)

다음 selector들의 `max-width: 1180px`을 `1440px`로 변경한다:

- `.job-portal-shell` (기본 규칙 + `@media (min-width: 760px)` 내부)
- `.job-portal-shell ~ .bottom-nav`
- `.site-header-top`
- `.site-gnb`
- `.home-hero-grid`
- `.home-category-grid`
- `.jobs-page-head`
- `.jobs-grid`

내부 padding(24px)은 그대로 유지하므로 좁은 화면 동작에는 영향이 없다.
`.jobs-grid`는 기본 5열(`repeat(5, minmax(0,1fr))`)을 유지하되, 컨테이너가
넓어지면서 카드 한 장의 폭이 커진다 (스크린샷 2번의 브랜드 카드 그리드와
유사한 비율).

### 2. GNB 핵심 기능 버튼 복원

`fragments/layout.html`의 `siteHeader` 프래그먼트, `site-gnb` 안에
`site-gnb-actions` div를 복원한다:

```html
<div class="site-gnb-actions">
  <a class="gnb-btn gnb-btn-worker" th:href="@{/worker/profile/edit}">구직 프로필 등록</a>
  <a class="gnb-btn gnb-btn-boss" th:href="@{/boss/recruitments/new}">공고 등록</a>
</div>
```

`style.css`에 53065b5에서 제거된 다음 규칙들을 복원한다:

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

`.site-gnb`가 `display:flex; justify-content:space-between;`이므로 메뉴와
액션 버튼이 자연스럽게 좌우로 배치된다. 640px 이하 등 좁은 화면에서
줄바꿈되어도(`flex-wrap: wrap`) 무방하다.

### 3. `.hero-banner-card` 화이트 톤 정리

"양방향 매칭" 소개 카드(`.hero-banner-card`)는 내용(소개 문구 + "양방향
매칭 시작하기" CTA)은 유지하되, 시각적으로 다른 카드와 동일한 화이트 톤으로
맞춘다.

- 기존 `.hero-banner-card { background: linear-gradient(...); color: white; ... }`
  규칙에서 그라데이션/흰 글자를 제거하고, `.hero-jobs-card`와 동일하게
  화이트(또는 `.public-portal` 영역에서는 `#F8F9FA`) 배경 + 테두리로 변경한다.
- `.hero-banner-card .eyebrow`는 강조 색(`var(--green)`)으로, `h1`/`p`는
  `var(--ink)`/`var(--gray-600)`로 변경한다.
- `.hero-banner-card .btn`(`.btn-primary`, 파란 배경)은 화이트 카드 위에서도
  그대로 잘 보이므로 변경하지 않는다.
- `1349`행 근처의 화이트 톤 override 셀렉터 목록
  (`.public-portal .hero-jobs-card, .public-login-card, .category-box`)에
  `.hero-banner-card`를 추가한다.

### 4. 회원가입(`signup.html`) 디자인 시스템 통합

#### 템플릿 구조 변경

- `<main class="app-shell">` → `<main class="app-shell job-portal-shell public-portal">`
- `fragments/layout :: topbar(...)` → `fragments/layout :: siteHeader`
  (홈/`/jobs`와 동일한 헤더 + GNB 노출)
- `section.screen.active signup-screen` → `section.screen.active no-bottom`
  (`signup-screen` 클래스 제거, 다른 공개 페이지와 동일한 `no-bottom` 사용)
- 폼 전체를 새 `.signup-page > .signup-card` 래퍼로 감싼다.
- `<form class="form-screen signup-form" ...>` → `<form class="local-auth-form" ...>`로
  클래스를 교체한다 (`form-screen`의 18px 좌우 패딩 + `signup-form` 오버라이드를
  모두 제거하고, `.public-login-card .local-auth-form`과 동일한 패턴으로
  `.signup-card`가 바깥 카드 역할을 하도록 함).
- `.page-intro`와 `.page-message`(에러 메시지)는 `<form>` 밖, `.signup-card`
  직속 자식으로 옮긴다. `<form>`에는 입력 필드 그룹(`이름`/`아이디`+중복확인/
  `비밀번호`)과 제출 버튼만 남는다.
- 폼 내부 필드 구조와 마크업(각 `form-group`, `email-check-row` 등)은 그대로
  유지한다 — `.form-group`, `.form-label`, `.form-input`, `.email-check-row`,
  `.email-check-btn`, `.form-help`, `.btn-primary` 등 기존 공용 클래스를
  계속 사용한다 (토스 전용 오버라이드 없이 홈의 `.public-login-card`
  로그인 폼과 동일한 모양이 됨).

최종 구조 예시:

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
          <!-- 이름 / 아이디(중복확인) / 비밀번호 form-group, 기존과 동일 -->
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

#### CSS 변경

- `1300`행부터의 "Signup (Toss-style)" 블록(`.signup-screen`,
  `.signup-form`, `.signup-form .page-intro h1` 등 전부)을 삭제한다.
- 새 블록 추가:

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

(`.public-login-card .local-auth-form { padding:0; border:0; }`과 동일한
패턴 — `.local-auth-form`의 기본 카드 스타일을 무력화하고 `.signup-card`가
바깥 카드 역할을 한다.)

## 비범위

- `worker_home`/`boss_home`/마이페이지 등 나머지 모바일 앱 셸(`.app-shell`,
  430px) 화면의 데스크톱 전환 — 다음 단계에서 진행.
- `/jobs` 페이지의 카드 마크업/필드 구성 변경 (현재 5열 그리드 구조 유지,
  컨테이너 폭만 넓어짐).
- 검색창/필터 입력(site-search) 등 헤더 자체의 기능 변경.

## 검증 방식

CSS/HTML 변경이며 자동화된 단위 테스트는 없다. 각 작업 후 `grep`으로
선택자/클래스 추가·삭제를 확인하고, 가능하면 `/run`으로 앱을 띄워 `/`,
`/jobs`, `/signup`을 브라우저에서 확인한다:

- `/`: GNB 우측에 "구직 프로필 등록"(초록)/"공고 등록"(파랑) 버튼이 보이고,
  "양방향 매칭" 카드가 다른 카드와 같은 화이트 톤인지, 화면이 넓은
  모니터에서 더 채워지는지 확인.
- `/jobs`: 카드 그리드가 더 넓어졌는지 확인.
- `/signup`: 홈과 동일한 헤더+GNB가 보이고, 폼이 흰 카드 안에 중앙
  정렬되어 있으며, 인풋/버튼 모양이 홈의 로그인 폼과 동일한지 확인.
