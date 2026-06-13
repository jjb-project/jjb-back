# 토스 스타일 회원가입 리디자인 + 홈 화면 화이트 톤 정리

## 배경

직전 단계(`2026-06-12-albamon-style-home-redesign.md`)에서 홈 화면을
알바천국 스타일 3단 히어로 + 카테고리 그리드 구조로 개편했다. 이번 단계는
같은 구조는 유지하되, 색감과 일부 UI를 더 미니멀하고 신뢰감 있는 톤
(토스/대기업 회원가입 페이지 톤)으로 다듬는다.

- 회원가입 페이지를 토스류 회원가입 화면처럼 화이트 배경 + 큰 타이포그래피 +
  보더리스 인풋 + 하단 고정 CTA로 리디자인한다.
- 홈 화면(`index.html`)의 배경을 화이트로 바꾸고, 광고성/중복 CTA처럼 보이는
  GNB 버튼 2개를 제거한다.

## 범위

1. 회원가입 페이지 (`signup.html`) 리디자인
2. 홈 화면(`index.html`) 화이트 톤 + GNB 액션 버튼 제거

## 1. 회원가입 페이지 (`signup.html`)

### 구조

기존 단일 폼 구조(이름 / 아이디+중복확인 / 비밀번호 / 제출)는 그대로
유지한다. 새로 추가하는 페이지 전용 클래스로 스코프를 한정해 다른 폼
페이지(`worker_register.html` 등)에 영향이 없도록 한다.

- `<form class="form-screen signup-form" ...>` 로 modifier 클래스 추가

### 변경 사항

- **배경**: `.signup-form`이 속한 화면을 화이트(`#FFFFFF`)로. `.app-shell`의
  기본 배경(연한 블루그레이)을 이 화면에서만 오버라이드.
- **타이틀**: `.signup-form .page-intro h1`을 28px / extrabold로 확대,
  `line-height` 넉넉하게. 설명문(`p`)은 14px, 연한 그레이(`var(--gray-400)`).
- **입력 필드**: `.signup-form .form-input`을 보더리스 + 연한 그레이 배경
  (`#F2F4F6`)으로, 높이 56px, `border-radius:12px`. focus 시 2px 블루 보더
  (`var(--blue)`)로 강조. 라벨(`.form-label`)은 작게 유지하되 톤 다운.
- **아이디 중복확인 버튼**: `.email-check-btn`을 새 인풋 높이(56px)에 맞춰
  정렬하고, 연한 회색 배경으로 톤 정리.
- **CTA**: 제출 버튼(`[data-signup-submit]`)을 화면 하단에 `position:sticky`로
  고정, 높이 56px, `border-radius:12px`. 기존 disabled/활성 로직(JS)은 변경
  없음 — 스타일만 변경.
- **제거**: "로그인으로 돌아가기" `<a class="btn btn-secondary">` 버튼을
  템플릿에서 삭제 (상단 topbar의 뒤로가기 버튼과 기능 중복).

## 2. 홈 화면 (`index.html`)

### 배경

- `.public-portal` (= `index.html`의 `.job-portal-shell.public-portal`)의
  배경을 `#F1F5F9` → `#FFFFFF`로 변경.
- `.home-hero-grid`, `.home-category-grid` 내부 카드(`.hero-jobs-card`,
  `.public-login-card`, `.category-box`)는 화이트 배경 위에서도 구분되도록
  보더 제거 + 연한 그레이 배경(`#F8F9FA`)으로 변경. `.hero-banner-card`(그린
  그라디언트 배너)는 현 상태 유지.

### GNB 액션 버튼 제거

- `fragments/layout.html`의 `siteHeader` 프래그먼트에서 `.site-gnb-actions`
  div(“구직 프로필 등록”, “공고 등록” 버튼)를 삭제. `.site-gnb-menu`(메뉴
  링크 6개)만 유지.
- 관련 CSS(`.site-gnb-actions`, `.gnb-btn`, `.gnb-btn-worker`,
  `.gnb-btn-boss`)도 함께 제거.
- `siteHeader`는 `index.html`과 `jobs.html`에서만 사용되므로 다른 페이지
  (worker_home, boss_home 등)에는 영향 없음.

## 비범위

- 회원가입 단계별(step-by-step) 플로우 전환 — 이번엔 한 화면 폼 유지
- `worker_register.html` 등 다른 폼 페이지의 톤 변경
- navy GNB 바 자체의 색상/구조 변경 (액션 버튼만 제거)
- `hero-banner-card`, `dual-match` 카테고리 박스 등 다른 콘텐츠 블록의 제거
