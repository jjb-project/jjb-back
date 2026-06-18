package project.jjb.common;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 업로드 용량 초과 등 멀티파트 파싱 예외를 페이지 리다이렉트로 변환한다.
 * 이렇게 하지 않으면 모바일 브라우저(특히 iOS Safari)가 본문 없는 413 응답을
 * 파일로 다운로드하려 한다.
 */
@ControllerAdvice
public class MultipartExceptionHandler {

	@ExceptionHandler({MaxUploadSizeExceededException.class, MultipartException.class})
	String handleUploadTooLarge(RedirectAttributes redirectAttributes, HttpServletRequest request) {
		redirectAttributes.addFlashAttribute("errorMessage",
			"사진 용량이 너무 큽니다. 더 작은 이미지를 선택하거나 사진 없이 저장해주세요.");
		String uri = request.getRequestURI();
		if (uri != null && uri.startsWith("/boss")) {
			return "redirect:/boss/verify";
		}
		return "redirect:/worker/profile/edit";
	}
}
