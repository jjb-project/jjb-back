# 모바일 앱 모드 재배치 설계

- 날짜: 2026-06-18
- 대상: `바로알바`(jjb) 전체 사용자-facing 화면
- 작성 배경: 모바일에서 레이아웃이 크게 깨진다는 피드백 → 모바일 기준으로 전면 재배치

## 목표

좁은 화면(휴대폰/소형 태블릿)에서 모든 페이지를 **단일 컬럼 + 하단 탭바 중심의 네이티브 앱 느낌**으로 재배치한다. 넓은 화면의 데스크톱 포털 레이아웃은 **현재 그대로 유지**한다.

## 비목표 (YAGNI)

- 데스크톱(1440px 포털) 디자인 변경 — 손대지 않는다.
- 새로운 기능/페이지 추가 — 순수 레이아웃·반응형 작업만.
- 역할별(구직자/사장님) 하단 탭 분기 — 고정 5탭으로 단순화.
- 백엔드/컨트롤러/도메인 변경 — 템플릿(HTML)과 CSS만 수정.

## 현재 구조 (조사 결과)

- 24개 사용자 템플릿 **전부** `body.web-page` + `fragments/layout :: siteHeader` + `fragments/layout :: scripts` 를 공유한다.
- 공유 헤더(`siteHeader`)는 `로고 + 480px 고정 검색창 + 액션 + GNB(6개 가로 메뉴)` 구조.
- 페이지별 본문 컨테이너: `web-narrow`(680px 폼), `web-detail`(960px 상세·목록), `web-mypage`(860px), `web-result`(480px), `login-page-wrap`(로그인/역할/가입), 그리고 홈/jobs는 1440px 풀폭 섹션.
- 구 `app-shell`/`topbar`/`bottom-nav` CSS는 현재 **어떤 템플릿에서도 사용하지 않는 죽은 코드**.

### 모바일이 깨지는 원인

1. `.site-header-top`이 `grid 1fr auto 1fr` + `.site-search{width:480px}` 고정 → 좁은 폭에서 가로 오버플로. 기존 860px 미디어쿼리의 `order/flex-basis`는 grid 컨테이너에서 의도대로 동작하지 않음.
2. `.site-header-actions`에 로고/로그인/회원가입/모드배지/벨/아바타가 한 줄 → 폭 부족.
3. `.site-gnb-menu` 6개 가로 메뉴가 스크롤 처리 없이 넘침.
4. 홈/jobs의 5열 카드 그리드가 좁은 폭에서 과밀.
5. 앱 느낌의 핵심인 하단 네비게이션이 없음.

## 설계

### 0. 분기 전략

- 단일 앱 모드 분기점 **`@media (max-width: 820px)`** 도입. 이 폭 이하 = "앱 모드"(1컬럼 + 하단 탭바 + 컴팩트 헤더). 초과 = 데스크톱 포털 그대로.
- 기존에 흩어진 480/560/860/1100px 미디어쿼리를 820 기준으로 재정리하여 중간 어색 구간 제거. (1100px의 데스크톱 다단 그리드 축소는 유지 가능.)

### 1. 모바일 헤더 (`fragments/layout.html` 의 `siteHeader` + CSS, 1곳 수정 → 전 페이지 반영)

앱 모드에서 3행 구성:

```
1행: ⚡바로알바              🔔  (모드배지)  👤
2행: 🔍 검색으로 딱! 알바 찾기            (전체폭)
3행: 채용정보 · 인재정보 · 대타마켓 · 제안 · 평가  (가로 스크롤 칩)
```

- `.site-header-top`을 앱 모드에서 flex-wrap 2행으로: 1행(로고 + 우측 액션), 2행(검색 전체폭). `.site-search{width:100%}`.
- 비로그인 시 `로그인/회원가입` 텍스트 링크는 숨기고 아바타(→로그인) 하나로 압축.
- `.site-gnb-menu`는 가로 스크롤 스트립으로(`overflow-x:auto`, 스크롤바 숨김, `white-space:nowrap`). 전체메뉴 버튼은 숨김 또는 칩화.
- 헤더는 `position:sticky; top:0` 유지.

### 2. 하단 탭바 (신규)

- 마크업은 `fragments/layout.html`의 **`scripts` 프래그먼트에 주입**한다(24개 페이지가 모두 `scripts`를 include하므로 페이지별 수정 0). `scripts` 프래그먼트가 `<nav class="app-tabbar">…</nav>` + 기존 `<script>`를 함께 렌더.
- 고정 5탭(역할 무관 동일): **홈(/) · 알바찾기(/jobs) · 대타(/substitutes) · 제안함(/inbox) · MY(/mypage)**. 각 탭 아이콘 + 라벨.
- 알림(벨)은 상단 헤더에 유지.
- 활성 표시: 현재 요청 경로(`#httpServletRequest.requestURI` 또는 컨트롤러가 넘기는 값)로 active 클래스. 안전하게 JS로 `location.pathname` 매칭하여 active 부여(서버 모델 의존 최소화).
- 데스크톱: `display:none`. 앱 모드(≤820px)에서만 `position:fixed; bottom:0` 표시. `padding-bottom: env(safe-area-inset-bottom)`.
- 본문이 탭바에 가리지 않도록: 앱 모드에서 `body.web-page` 또는 각 컨테이너에 `padding-bottom`(탭바 높이 ~64px + safe-area) 추가.

### 3. 홈(index.html) 모바일 재배치 (CSS 위주, 필요한 최소 마크업 조정)

- `.home-hero-grid` 3컬럼 → 세로 스택. 순서: **배너 → 로그인/내 카드 → 오늘의 업데이트**. (소스 순서 또는 `order` 활용.)
- `.home-category-grid` → 세로 스택. 내부 `.category-box-grid`는 2~3열 유지하되 탭 영역 충분히 크게.
- `.home-portal-grid`/`.home-jobs-grid` 5열 → **2열**. 카드 내부 간격·폰트 모바일 조정.
- `.portal-filter-bar` select들 → 가로 스크롤 또는 2열 wrap.
- 급구 레일(`.urgent-rail`) 가로 스크롤 유지.

### 4. jobs.html

- `.jobs-grid` 5열 → 2열. `.jobs-page-head` 패딩 축소.

### 5. 나머지 페이지 전수 점검 (헤더/탭패딩은 공통 적용으로 자동 해결, 본문만 점검)

- `web-narrow`(폼): `worker_profile, boss_post, boss_post_edit, boss_verify, chat, eval, substitute_new` — 1컬럼이라 대체로 OK. 폼 하단 제출 버튼과 탭바 겹침 방지 여백, `inline-field`/`select-grid`/`time-range-fields`/`email-check-row` 등 좁은 폭 점검.
- `web-detail`(상세·목록): `job_detail, candidate_detail, store_detail, request_detail, boss_recruitment_detail, boss_manage, inbox, notifications, substitutes` — `.detail-grid 1fr 1fr`, `.jd-info`, `.action-row`, `.jd-hero`, `inbox-card`/`sub-card`(기존 560 쿼리) 점검·정리.
- `web-mypage`: `mypage` — 헤더·메뉴 리스트 점검.
- `web-result`: `match_confirmed, worker_register` — 중앙 정렬 결과 화면, 탭바와 충돌 없는지.
- `login-page-wrap`: `login, role, signup` — 카드 폭/패딩 모바일 점검. 로그인/가입/역할 화면에서 하단 탭바 노출 여부 결정(로그인 전 화면은 탭바 숨김 검토 — 단, 단순화를 위해 기본은 노출, 탭 클릭 시 로그인 유도로 충분).

### 6. 클린업

- 죽은 `.app-shell`, 구 `.bottom-nav`/`.nav-item`/`.topbar` 관련 CSS는 새 `.app-tabbar` 스타일로 대체·삭제. (단, 구 클래스가 어디서도 안 쓰임을 재확인 후 제거.)

## 산출물 / 영향 범위

- 수정: `src/main/resources/templates/fragments/layout.html` (헤더 마크업, scripts에 탭바 주입)
- 수정: `src/main/resources/static/css/style.css` (앱 모드 미디어쿼리 전면 정리 + 탭바 스타일 + 클린업)
- 수정: `src/main/resources/templates/index.html`, `jobs.html` 등 일부 본문 마크업(필요 시 최소)
- 백엔드 변경 없음.

## 검증

- `./gradlew compileJava` (템플릿/정적 자산만 바꾸지만 빌드 무결성 확인) 및 기존 `./gradlew test` 통과 유지.
- 수동: 브라우저 반응형(375px iPhone SE, 390px, 768px, 1024px, 1440px)에서 홈/jobs/상세/폼/로그인 렌더 확인 — 가로 오버플로 없음, 탭바 동작, 헤더 정상.

## 미해결/결정 사항

- 로그인 전 화면(login/signup/role)에서 하단 탭바 노출: 기본 노출로 진행(추후 조정 가능).
- 앱 모드 분기점 820px: 홈 히어로의 기존 860px 붕괴를 820으로 통일.
