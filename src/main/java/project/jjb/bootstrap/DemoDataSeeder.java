package project.jjb.bootstrap;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import project.jjb.matching.domain.MatchRequestSnapshot;
import project.jjb.matching.domain.Recruitment;
import project.jjb.matching.service.MatchingService;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.service.MemberService;
import project.jjb.notification.domain.NotificationType;
import project.jjb.notification.service.NotificationService;

/**
 * 시연용 데모 데이터를 애플리케이션 기동 시 한 번 채워 넣는다.
 *
 * <p>현재 datasource는 {@code ddl-auto: create}라 재시작할 때마다 스키마와 데이터가 비워지므로,
 * 기동 직후 시딩하면 매 시연마다 동일한 상태가 자동으로 복구된다. 이미 시드 계정({@code boss})이
 * 존재하면 아무 것도 하지 않는다(중복 방지).
 *
 * <p>도메인을 직접 건드리지 않고 컨트롤러가 사용하는 것과 동일한 서비스 메서드만 호출한다.
 */
@Component
class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
	private static final String SEED_PASSWORD = "demo1234";
	private static final String STORE_NAME = "강남 베이글하우스";
	private static final String STORE_ADDRESS = "서울특별시 강남구 테헤란로 123";

	private final MemberService memberService;
	private final MatchingService matchingService;
	private final NotificationService notificationService;

	DemoDataSeeder(MemberService memberService, MatchingService matchingService,
			NotificationService notificationService) {
		this.memberService = memberService;
		this.matchingService = matchingService;
		this.notificationService = notificationService;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!memberService.checkLocalUsernameAvailability("boss").available()) {
			log.info("[DemoDataSeeder] 데모 데이터가 이미 존재하여 시딩을 건너뜁니다.");
			return;
		}
		try {
			seed();
			log.info("[DemoDataSeeder] 데모 데이터 시딩 완료 (로그인 비밀번호: {})", SEED_PASSWORD);
		} catch (Exception e) {
			log.warn("[DemoDataSeeder] 데모 데이터 시딩 중 오류가 발생했습니다: {}", e.getMessage(), e);
		}
	}

	private void seed() {
		// 1) 사장님 계정 + 사업자 인증 + 매장
		UUID boss = registerOwner();

		// 2) 구직자 3명 + 이력서
		UUID worker1 = registerSeeker("worker1", "이지원",
			"평일 오전·주말", "서울특별시 강남구", 11000,
			List.of("카페", "매장"), "1~3년", "여",
			"카페 홀 경험 2년. 친절하고 성실하게 일합니다.");
		UUID worker2 = registerSeeker("worker2", "박민수",
			"주말·야간", "서울특별시 마포구", 10500,
			List.of("편의점", "식당"), "신입", "남",
			"편의점·주방 보조 가능. 야간 근무 환영합니다.");
		UUID worker3 = registerSeeker("worker3", "최유나",
			"평일 전일", "경기도 성남시", 12000,
			List.of("배달", "물류"), "3년 이상", "여",
			"배달·물류 경력 3년. 빠르고 정확합니다.");

		// 3) 공고 4개 (급구/즉시확정 혼합)
		LocalDate today = LocalDate.now();
		Recruitment job1 = matchingService.createRecruitment(boss,
			"주말 카페 홀서빙 급구", today.plusDays(2), LocalTime.of(10, 0), LocalTime.of(18, 0),
			STORE_ADDRESS, 11000, List.of("급구", "주말"),
			"주말 홀서빙 단기 알바입니다. 즉시확정 가능!", true);
		Recruitment job2 = matchingService.createRecruitment(boss,
			"평일 오전 베이커리 보조", today.plusDays(3), LocalTime.of(7, 0), LocalTime.of(12, 0),
			STORE_ADDRESS, 10500, List.of("주방"),
			"오전 베이커리 진열·보조 업무입니다.", false);
		Recruitment job3 = matchingService.createRecruitment(boss,
			"마감 청소·정리 단기알바", today.plusDays(1), LocalTime.of(21, 0), LocalTime.of(23, 0),
			STORE_ADDRESS, 12000, List.of("급구"),
			"마감 청소 및 정리. 2시간 단기 즉시확정.", true);
		matchingService.createRecruitment(boss,
			"주 3일 주방보조 모집", today.plusDays(5), LocalTime.of(11, 0), LocalTime.of(15, 0),
			STORE_ADDRESS, 10800, List.of("주방"),
			"주 3일 점심 주방보조. 장기 가능.", false);

		// 4) 진행 중 매칭 1건: 김사장 → 이지원, 공고1 제안 → 수락 → 채팅 → 평가
		MatchRequestSnapshot match = matchingService.createMatchRequest(boss, worker1, job1.id(),
			"이력서 보고 연락드려요. 이번 주말 홀서빙 함께 하실 수 있을까요?");
		notificationService.notify(worker1, NotificationType.MATCH_REQUEST,
			"새 매칭 제안이 도착했어요",
			STORE_NAME + "에서 '주말 카페 홀서빙 급구' 제안을 보냈어요.", "/inbox");

		matchingService.acceptMatchRequest(match.id(), worker1);
		notificationService.notify(boss, NotificationType.MATCH_ACCEPTED,
			"이지원님이 제안을 수락했어요",
			"'주말 카페 홀서빙 급구' 매칭이 확정되었습니다.", "/inbox");

		matchingService.sendChatMessage(match.id(), boss, "안녕하세요! 토요일 오전 10시 출근 가능하실까요?");
		matchingService.sendChatMessage(match.id(), worker1, "네 가능합니다. 잘 부탁드려요!");
		matchingService.sendChatMessage(match.id(), boss, "좋아요, 그날 뵙겠습니다 😊");

		matchingService.createReview(match.id(), boss, worker1, 5,
			"성실하고 밝아서 함께 일하기 좋았어요. 또 모시고 싶습니다.");
		notificationService.notify(worker1, NotificationType.REVIEW_RECEIVED,
			"새 후기가 등록되었어요",
			STORE_NAME + "가 별점 5점 후기를 남겼어요.", "/eval");

		// 5) 대타 요청 2건 (대타 마켓 노출용)
		matchingService.createSubstituteRequest(worker2, boss, job2.id(),
			STORE_NAME, "이번 주 토요일 07:00~12:00 베이커리 보조",
			"갑작스러운 집안 일정으로 대타 구합니다.");
		matchingService.createSubstituteRequest(worker3, boss, job3.id(),
			STORE_NAME, "금요일 21:00~23:00 마감 청소",
			"다른 일정과 겹쳐 대타 부탁드려요.");
	}

	private UUID registerOwner() {
		MemberSnapshot owner = memberService.registerLocalMember("boss", SEED_PASSWORD, "김사장");
		memberService.switchRole(owner.id(), MemberRole.OWNER);
		memberService.updateOwnerProfile(owner.id(),
			STORE_NAME, STORE_ADDRESS, "카페",
			"갓 구운 베이글과 커피를 파는 동네 카페입니다.", null);
		memberService.verifyBusiness(owner.id(), "123-45-67890", "김사장", LocalDate.now().minusYears(2));
		return owner.id();
	}

	private UUID registerSeeker(String username, String displayName, String availableTime,
			String preferredArea, int desiredHourlyWage, List<String> industries,
			String careerLevel, String gender, String introduction) {
		MemberSnapshot seeker = memberService.registerLocalMember(username, SEED_PASSWORD, displayName);
		memberService.switchRole(seeker.id(), MemberRole.JOB_SEEKER);
		memberService.updateJobSeekerProfile(seeker.id(),
			availableTime, preferredArea, desiredHourlyWage, industries,
			true, introduction, gender, "해당없음", "고등학교 졸업", careerLevel,
			List.of("토", "일"), null);
		return seeker.id();
	}
}
