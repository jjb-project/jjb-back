package project.jjb.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiError> handleApiException(ApiException exception) {
		return ResponseEntity
			.status(exception.status())
			.body(ApiError.of(exception.code(), exception.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException exception) {
		return ResponseEntity
			.badRequest()
			.body(ApiError.of("VALIDATION_FAILED", "Request body validation failed."));
	}
}
