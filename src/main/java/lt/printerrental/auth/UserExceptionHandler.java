package lt.printerrental.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class UserExceptionHandler {
  @ExceptionHandler(CustomerProvisionException.class)
  ProblemDetail customerProvision(CustomerProvisionException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(error.status()), error.getMessage());
  }
  @ExceptionHandler(UserNotFoundException.class)
  ProblemDetail notFound(UserNotFoundException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, error.getMessage());
  }

  @ExceptionHandler(UserRoleConflictException.class)
  ProblemDetail roleConflict(UserRoleConflictException error) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, error.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail badRequest(IllegalArgumentException error) {
    String message = error.getMessage();
    if (message == null || message.isBlank()) {
      return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
    }
    String safe = message.replaceAll("[\\r\\n]+", " ").strip();
    if (safe.length() > 200) {
      safe = safe.substring(0, 200);
    }
    return ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST, safe.isBlank() ? "Invalid request" : safe);
  }
}
