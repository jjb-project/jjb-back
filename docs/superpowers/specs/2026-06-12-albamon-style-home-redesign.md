# 알바천국 스타일 홈 화면 + 공고 전체보기 마이그레이션 (1단계)

## 배경

"바로알바"는 현재 모바일 앱 셸(430px) 형태의 구인구직 + 양방향 매칭 MVP다.
전체 사이트를 데스크톱 풀사이즈 웹사이트(알바천국 스타일)로 단계적으로
전환하기로 했다. 이번 1단계는 공통 헤더/GNB 디자인 시스템과 메인 홈 화면,
그리고 신규 "실시간 공고 전체보기" 페이지를 다룬다.

색상/브랜딩은 기존 바로알바 초록/파랑 팔레트를 유지하고, 알바천국 스크린샷의
**레이아웃/구조**만 차용한다. 브랜드 로고 캐러셀, 외부 광고 배너(K-HIRE,
DAEKYO 등)는 제외한다.

## 범위

1. 공통 데스크톱 헤더 + GNB 디자인 시스템 (CSS + Thymeleaf 프래그먼트)
2. 홈 화면(`index.html`) 전면 개편
3. 신규 "실시간 공고 전체보기" 페이지 (`/jobs`)

## 1. 공통 헤더 / GNB

### 구조

- `fragments/layout.html`에 `siteHeader` 프래그먼트 추가 (다음 단계에서 다른
  페이지도 재사용할 수 있도록 분리)
- 상단 바: 로고("바로알바") + 통합 검색 폼(키워드/지역/업종, 1행) + 우측
  로그인/회원가입 링크 (비로그인 시) — 기존 `home-actions` 영역 패턴 재사용
- GNB 바 (네이비 배경, 기존 `.portal-menu` 스타일 확장):
  `채용정보 | 인재정보 | 지역별 | 업종별 | 평가관리 | 양방향 매칭`
  우측에 액션 버튼 2개: `구직 프로필 등록`(초록, `/worker/profile/edit`),
  `공고 등록`(파랑, `/boss/recruitments/new`)
- 컨테이너 폭: 기존 `.job-portal-shell`(max-width 1180px) 패턴을 홈/리스트형
  페이지에 적용

### CSS

- 새 클래스: `.site-header`, `.site-header-top`, `.site-search`,
  `.site-gnb`, `.site-gnb-actions`
- 기존 `.portal-menu`, `.home-topbar` 스타일을 베이스로 확장 (완전히 새로
  만들지 않음)
- 768px 이하에서는 검색창/GNB가 줄바꿈되도록 반응형 처리

## 2. 홈 화면 (`index.html`)

### 3단 히어로 그리드 (`.home-hero-grid`, 데스크톱 3열 / 모바일 1열)

- **왼쪽 (`실시간 등록 공고`)**: 최근 등록된 OPEN 공고 4~5건을 리스트 카드로
  표시 (매장명, 시급, 근무 일시, 주소). "더보기" 링크 → `/jobs`
- **가운데 (`양방향 매칭` 홍보 배너)**: 구직자 지원 ↔ 사장님 제안 흐름을
  설명하는 배너. CTA 버튼 "시작하기" → `/role`
- **오른쪽 (로그인 카드)**: 기존 `index.html`의 로그인 폼 + 소셜 로그인 버튼을
  그대로 이동

### 하단 3열 카테고리 박스 (`.home-category-grid`)

- **지역·동네 알바**: 주요 지역(서울/경기/부산/대구/인천 등) 링크 →
  `/worker/home?region=...`
- **대상별 알바**: 대학생/주부/청소년/외국인 등 키워드 링크 →
  `/worker/home?keyword=...`
- **양방향 매칭 가이드**: 지원현황 / 받은 제안 / 평가관리 / 공고등록 4개
  바로가기 (각각 `/worker/home`, `/boss/home`, `/eval`, `/boss/recruitments/new`)

### 기존 `dual-match-strip`, `portal-feature-grid`는 새 구조에 맞춰 재배치하거나
일부 카테고리 박스로 흡수하여 중복을 제거한다.

## 3. 신규 "실시간 공고 전체보기" 페이지 (`/jobs`)

### 라우트

- `GET /jobs` — 로그인 불필요 (공개 페이지), `JjbPageController`에 추가
- 모든 `RecruitmentStatus.OPEN` 공고를 최신순으로 조회하여 카드 그리드로 표시

### 데이터

- 신규 DTO `JobListingCard(id, title, storeName, businessCategory, workplaceAddress, workTime, hourlyWage, statusLabel)`
- `MatchingService.listRecruitments()` 결과를 OPEN 상태로 필터링하고,
  `MemberService.getMember(ownerId)`로 매장명/업종을 조인
- 홈 화면의 "실시간 등록 공고" 섹션도 동일 DTO를 사용 (상위 5건만)

### 템플릿 (`jobs.html`)

- 스크린샷 2의 카드 그리드 스타일(데스크톱 5열, 태블릿 3열, 모바일 1~2열)
- 카드: 매장명, 업종 배지, 공고 제목, 시급, 근무 일시/주소, "상세보기" 버튼
  → `/worker/stores/{ownerId}` 또는 로그인 후 지원 가능 안내
- 비로그인 사용자도 목록은 볼 수 있되, "지원하기"는 로그인/역할 선택 페이지로
  유도

## 비범위 (다음 단계)

- worker_home / boss_home / 상세 페이지 등 나머지 화면의 데스크톱 전환
- 양방향 매칭 플로우 UI 재배치
- 브랜드 그리드 전용 페이지, 외부 광고 배너
