# 마이페이지 매칭 히스토리 설계

## 배경 / 문제

현재 사용자가 "성공한 매칭" 목록을 한곳에서 볼 수 있는 화면이 없다.

- `/inbox` (매칭 제안함)은 요청을 받은/보낸 것을 모두 보여주지만, 진행중(REQUESTED)·거절(DECLINED)·취소(CANCELED)가 섞여 있어 "성공한 매칭만" 모아 보는 용도가 아니다.
- 대타 매칭(`SubstituteRequest`가 FILLED 된 건)은 어디에도 히스토리로 남지 않는다.

사용자는 마이페이지에서 **성공한 매칭 이력**을 모아 보고 싶어 하며, **대타 매칭도 함께** 보이길 원한다.

## 목표

마이페이지에서 진입하는 전용 페이지(`/mypage/matches`)에서, 로그인한 회원이 참여해 성사된 매칭을 최신순 단일 리스트로 보여준다. 정식 매칭과 대타 매칭을 한 리스트에 통합하되 종류를 배지로 구분한다.

## 비목표 (YAGNI)

- 근무 "완료" 상태 추적: 도메인에 완료 개념이 없다. ACCEPTED(정식)와 FILLED(대타)를 "성공"으로 간주하고, 별도 완료 상태/플로우를 도입하지 않는다.
- 거절·취소된 건은 히스토리에 포함하지 않는다. (그 정보는 매칭 제안함에 그대로 남는다.)
- 정렬/필터/페이지네이션 UI는 추가하지 않는다. 단순 최신순 리스트로 충분하다.

## 화면 / 진입점

- `mypage.html`의 메뉴 리스트(`.menu-list`)에 항목 추가:
  - 라벨 "매칭 히스토리", 아이콘 `fa-solid fa-handshake`, 링크 `/mypage/matches`
  - 기존 메뉴 항목(`구직자 프로필`, `평가하기`)과 동일한 `.menu-item` 마크업을 따른다.
- 신규 템플릿 `match_history.html`:
  - 헤더, `N건` 카운트, empty-state, 카드 리스트로 구성한다.
  - 기존 `inbox.html`의 카드 스타일(`.inbox-card`, `.inbox-card-main`, `.inbox-card-top`, `.inbox-status` 등)과 `mypage.html`의 `.section-header` 패턴을 재사용한다. 새 CSS 클래스는 최소화한다.

## 표시 내용

성공한 매칭만, 최신순 통합 리스트. 각 카드는 종류 배지(`정식 매칭` / `대타`)로 구분한다.

### 정식 매칭

- 소스: `MatchingService.listAcceptedMatchRequestsForParticipant(memberId)` (status == ACCEPTED).
- 카드 표시:
  - 상대방 이름: 내가 사장님이면 구직자 `displayName()`, 내가 구직자면 매장명(`storeName(owner)`).
  - 컨텍스트: `recruitmentId`가 있으면 공고 제목, 없으면 `message`.
  - 날짜: `respondedAt`(없으면 `createdAt`).
  - `대화` 버튼: `/chat/{matchRequestId}` 로 연결 (ACCEPTED 매칭은 채팅 가능).
- 정렬 키: `respondedAt`(없으면 `createdAt`).

### 대타 매칭

두 방향 모두 포함한다.

1. 내가 맡은 대타: `filledById == memberId`.
   - 상대/컨텍스트: 매장명(`storeName`)과 근무정보(`shiftInfo`). 배지 문구 예 "대타 · 내가 맡음".
2. 내가 구한 대타: `requesterId == memberId` 이고 `status == FILLED`.
   - 상대/컨텍스트: 맡아준 사람 `displayName()`, 매장명(`storeName`)·근무정보(`shiftInfo`). 배지 문구 예 "대타 · 구함 완료".
- 날짜/정렬 키: `createdAt`.
- 대타에는 채팅이 없으므로 `대화` 버튼을 표시하지 않는다.

> `SubstituteRequest`는 `storeName`, `shiftInfo`를 자체 필드로 들고 있으므로 공고 조회 없이 표시할 수 있다.

## 백엔드 변경

### 1. 레포지토리: "내가 맡은 대타" 조회 추가

- `SubstituteRequestJpaDataRepository`: `List<SubstituteRequestJpaEntity> findByFilledByIdOrderByCreatedAtDesc(UUID filledById)` 추가.
- `MatchingRepository` 인터페이스: `List<SubstituteRequest> findSubstituteRequestsByFilledById(UUID filledById)` 추가.
- `JpaMatchingRepository`: 위 데이터 메서드를 호출하도록 구현.

기존 "내가 구한 대타"는 `findSubstituteRequestsByRequesterId`로 이미 조회 가능하므로 재사용한다.

### 2. 서비스

- `MatchingService.listSubstituteRequestsByFiller(UUID memberId)` 추가: `findSubstituteRequestsByFilledById` 호출, FILLED만 노출(필터). `@Transactional(readOnly = true)`.
- 기존 `listSubstituteRequestsByRequester`는 그대로 두고, 컨트롤러에서 FILLED만 필터링한다.

### 3. 컨트롤러 (`JjbPageController`)

- `GET /mypage/matches` 핸들러 추가:
  - `requireMember(session)`로 회원 확인.
  - 정식 매칭 카드 + 대타 매칭 카드(맡은/구한)를 모아 정렬 키 기준 최신순으로 합쳐 모델에 추가.
  - 반환 뷰: `match_history`.
- 신규 `record MatchHistoryCard(String kindLabel, String kindCode, String counterpartName, String context, String detail, String statusLabel, String dateLabel, java.util.UUID chatMatchRequestId)` (필드 구성은 구현 시 화면 요구에 맞게 확정; `chatMatchRequestId`가 null이면 대화 버튼 미표시).
  - `kindCode`: `"MATCH"` 또는 `"SUBSTITUTE"` — 템플릿 배지 스타일 분기에 사용.
- 카드 빌더는 기존 헬퍼(`storeName`, `statusLabel`, 날짜 포맷 헬퍼 `shortTime(Instant)` — "MM/dd HH:mm")를 재사용한다.

## 데이터 흐름

```
GET /mypage/matches
  -> requireMember(session)
  -> 정식: matchingService.listAcceptedMatchRequestsForParticipant(me)
            -> 각 건 MatchHistoryCard(kind=MATCH, chat 링크 포함)
  -> 대타(맡은): matchingService.listSubstituteRequestsByFiller(me)
            -> MatchHistoryCard(kind=SUBSTITUTE)
  -> 대타(구함): matchingService.listSubstituteRequestsByRequester(me) where status==FILLED
            -> MatchHistoryCard(kind=SUBSTITUTE)
  -> 합쳐서 날짜 desc 정렬 -> model "historyCards"
  -> view "match_history"
```

## 에러 / 엣지 케이스

- 비로그인: 기존 `requireMember`가 처리(로그인 리다이렉트) — 다른 마이페이지 핸들러와 동일.
- 매칭 0건: empty-state 문구 표시 ("아직 성공한 매칭이 없습니다.").
- 상대 회원/공고 조회 실패: 정식 매칭 컨텍스트 조회는 기존 `inboxCard`처럼 try/catch로 빈 값 처리. 대타는 스냅샷 필드를 쓰므로 조회 의존이 적다.
- 내가 구한 대타가 아직 안 채워진(OPEN) 경우: status==FILLED 필터로 제외.

## 테스트

- `MatchingService`: `listSubstituteRequestsByFiller`가 filledById로 FILLED 건만 반환하는지 단위/슬라이스 테스트(기존 매칭 테스트 패턴을 따른다, test profile/fake 우선).
- 컨트롤러 레벨 검증은 기존 테스트 구성에 맞춰 최소화하되, `/mypage/matches`가 정식+대타를 합쳐 노출하는지 확인 가능한 수준으로 둔다.
- 빌드/테스트 확인: `./gradlew compileJava`, `./gradlew test`.

## 영향 받는 파일 (예상)

- `src/main/resources/templates/mypage.html` (메뉴 항목 1개 추가)
- `src/main/resources/templates/match_history.html` (신규)
- `src/main/java/project/jjb/web/controller/JjbPageController.java` (핸들러 + record + 빌더)
- `src/main/java/project/jjb/matching/service/MatchingService.java` (조회 메서드 1개)
- `src/main/java/project/jjb/matching/repository/MatchingRepository.java` (메서드 시그니처 1개)
- `src/main/java/project/jjb/matching/repository/persistence/JpaMatchingRepository.java` (구현)
- `src/main/java/project/jjb/matching/repository/persistence/SubstituteRequestJpaDataRepository.java` (쿼리 메서드 1개)
- 필요 시 CSS 소폭 추가(`src/main/resources/static/css`) — 배지 정도. 가급적 기존 클래스 재사용.
