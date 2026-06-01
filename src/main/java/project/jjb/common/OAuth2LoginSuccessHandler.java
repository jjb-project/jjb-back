package project.jjb.common;

import java.io.IOException;

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
		String subject = attribute(principal, "sub", principal.getName());
		String displayName = displayName(principal);
		MemberSnapshot member = memberService.createMember(provider, subject, displayName);

		request.getSession(true).setAttribute(WebSessionKeys.CURRENT_MEMBER_ID, member.id());
		request.getSession(true).setAttribute(WebSessionKeys.ACTIVE_ROLE, member.activeRole());
		response.sendRedirect(nextPath(member));
	}

	private String displayName(OAuth2User principal) {
		String name = attribute(principal, "name", null);
		if (!isBlank(name)) {
			return name;
		}
		String email = attribute(principal, "email", null);
		if (!isBlank(email)) {
			int atIndex = email.indexOf('@');
			return atIndex > 0 ? email.substring(0, atIndex) : email;
		}
		return "바로알바 회원";
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

	private String attribute(OAuth2User principal, String name, String defaultValue) {
		Object value = principal.getAttributes().get(name);
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		return defaultValue;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
