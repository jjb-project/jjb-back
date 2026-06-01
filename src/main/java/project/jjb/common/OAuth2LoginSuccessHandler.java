package project.jjb.common;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import project.jjb.member.domain.MemberRole;
import project.jjb.member.domain.MemberSnapshot;
import project.jjb.member.service.MemberService;
import project.jjb.web.WebSessionKeys;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private final MemberService memberService;

	public OAuth2LoginSuccessHandler(MemberService memberService) {
		this.memberService = memberService;
	}

	@Override
	public void onAuthenticationSuccess(
		HttpServletRequest request,
		HttpServletResponse response,
		Authentication authentication
	) throws IOException, ServletException {
		if (!(authentication instanceof OAuth2AuthenticationToken oauth2Authentication)) {
			response.sendRedirect("/");
			return;
		}

		OAuth2User principal = oauth2Authentication.getPrincipal();
		String provider = oauth2Authentication.getAuthorizedClientRegistrationId();
		String subject = subject(provider, principal);
		String displayName = displayName(provider, principal);
		MemberSnapshot member = memberService.createMember(provider, subject, displayName);

		request.getSession(true).setAttribute(WebSessionKeys.CURRENT_MEMBER_ID, member.id());
		request.getSession(true).setAttribute(WebSessionKeys.ACTIVE_ROLE, member.activeRole());
		response.sendRedirect(nextPath(member));
	}

	private String subject(String provider, OAuth2User principal) {
		if ("naver".equals(provider)) {
			return attributePath(principal, principal.getName(), "response", "id");
		}
		if ("kakao".equals(provider)) {
			return attributePath(principal, principal.getName(), "id");
		}

		String subject = attributePath(principal, null, "sub");
		if (!isBlank(subject)) {
			return subject;
		}
		return attributePath(principal, principal.getName(), "id");
	}

	private String displayName(String provider, OAuth2User principal) {
		String name = providerDisplayName(provider, principal);
		if (!isBlank(name)) {
			return name;
		}

		String email = email(provider, principal);
		if (!isBlank(email)) {
			int atIndex = email.indexOf('@');
			return atIndex > 0 ? email.substring(0, atIndex) : email;
		}
		return "바로알바 회원";
	}

	private String providerDisplayName(String provider, OAuth2User principal) {
		if ("naver".equals(provider)) {
			String name = attributePath(principal, null, "response", "name");
			if (!isBlank(name)) {
				return name;
			}
			return attributePath(principal, null, "response", "nickname");
		}
		if ("kakao".equals(provider)) {
			String name = attributePath(principal, null, "kakao_account", "profile", "nickname");
			if (!isBlank(name)) {
				return name;
			}
			return attributePath(principal, null, "properties", "nickname");
		}
		return attributePath(principal, null, "name");
	}

	private String email(String provider, OAuth2User principal) {
		if ("naver".equals(provider)) {
			return attributePath(principal, null, "response", "email");
		}
		if ("kakao".equals(provider)) {
			return attributePath(principal, null, "kakao_account", "email");
		}
		return attributePath(principal, null, "email");
	}

	private String nextPath(MemberSnapshot member) {
		if (!member.verification().phoneVerified()) {
			return "/phone";
		}
		if (member.activeRole() == null) {
			return "/role";
		}
		return member.activeRole() == MemberRole.OWNER ? "/boss/home" : "/worker/home";
	}

	private String attributePath(OAuth2User principal, String defaultValue, String... path) {
		Object value = principal.getAttributes();
		for (String key : path) {
			if (!(value instanceof Map<?, ?> values)) {
				return defaultValue;
			}
			value = values.get(key);
		}
		if (value == null) {
			return defaultValue;
		}
		String text = value.toString();
		if (!text.isBlank()) {
			return text;
		}
		return defaultValue;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
