# Repository Agents Guide

이 파일은 `jjb`에서 항상 유지해야 할 repo-wide 기준만 담는다. 상세 화면 흐름이나 일회성 작업 절차는 별도 문서로 분리한다.

## What

- `jjb`는 긴급 대타와 단기 알바를 빠르게 연결하는 매칭 서비스다.
- 초기 MVP는 `가입/로그인 -> 역할 선택 -> 구직자 프로필 등록 -> 사장님 후보 조회/매칭 요청 -> 구직자 수락 -> 근무 완료 -> 양방향 평가` 흐름을 우선한다.
- 핵심 도메인은 구직자, 사장님, 역할 전환, 사업자 인증, 매칭, 양방향 평가, 노쇼/취소 기록이다.
- 기술 스택은 Java 26, Spring Boot 4, Spring MVC, Thymeleaf, Spring Data JPA, Validation, PostgreSQL, Spring AI Google GenAI다.
- 사용자-facing MVP는 Spring MVC + Thymeleaf 서버 렌더링 중심으로 구현하고, REST API는 문서화, 테스트, 비동기/외부 연동이 필요한 영역의 보조 표면으로 유지한다. 패키지는 기능/도메인 단위로 성장시키되, 현재 초기 골격에서는 불필요한 계층과 파일을 먼저 만들지 않는다.

## Why

- 이 서비스는 당일/단기 근무 매칭에서 속도와 신뢰를 함께 확보해야 한다.
- MVP 신뢰 장치는 휴대폰 본인인증이 아니라 사업자 인증, 양방향 평가, 노쇼/취소 기록을 중심으로 둔다.
- 사업자 인증은 가입 직후 강제하지 않는다. 사장님 모드 탐색과 후보 조회는 허용하되 첫 매칭 요청 시점에 요구한다.
- 구직자만 평가받는 구조를 피하고, 사장님도 평가와 패널티 대상이 되게 하여 양방향 신뢰를 유지한다.
- Spring AI의 제품 내 역할은 아직 확정하지 않았다. AI 기능을 추가할 때는 매칭 신뢰성, 사용자 안전성, 테스트 격리에 미치는 영향을 함께 검토한다.

## How

- 기본 빌드 확인은 `./gradlew compileJava`를 사용한다.
- 기본 테스트 확인은 `./gradlew test`를 사용한다.
- 테스트는 실제 PostgreSQL 인스턴스나 실제 Google GenAI 키에 직접 의존하지 않도록 test profile, mock, fake 구성을 우선한다.
- 외부 DB, 사업자 진위 확인 API, GenAI 호출이 필요한 통합 테스트는 단위/슬라이스 테스트와 분리한다.
- 서버 렌더링 화면은 `src/main/resources/templates`, CSS/JS/image 같은 정적 자산은 `src/main/resources/static` 아래에 둔다.
- 로컬 개발용 `application.yaml` 값은 당장 허용하지만, 새 기능의 테스트 성공 조건을 개인 로컬 설정이나 실제 API 키에 묶지 않는다.
- repo-local 하네스 작업은 `.agents/skills/harness/SKILL.md`를 따른다. `AGENTS.md`, 신규 스킬, 팀 스펙, `_workspace/` 핸드오프를 만들거나 고칠 때는 먼저 이 하네스를 기준으로 범위와 산출물을 정한다.
- `AGENTS.md`에는 항상 적용되는 짧은 기준만 남기고, 조건부 워크플로와 긴 역할 설명은 `.agents/skills/` 또는 `docs/harness/` 아래로 분리한다.
