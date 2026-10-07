package com.saaketh.budget.common;

import com.saaketh.budget.auth.EmailAlreadyRegisteredException;
import com.saaketh.budget.auth.InvalidPasswordException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns exceptions into consistent JSON error responses using ProblemDetail (RFC 9457), e.g.
 * {"status":400,"title":"Bad Request","detail":"...","errors":{"email":"must be a well-formed email address"}}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** A request body failed @Valid checks (missing field, bad email, password too short...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> errors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** The body wasn't valid JSON at all. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadable(HttpMessageNotReadableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ProblemDetail handleInvalidPassword(InvalidPasswordException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request");
        problem.setProperty("errors", Map.of("password", e.getMessage()));
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    ProblemDetail handleEmailTaken(EmailAlreadyRegisteredException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Wrong email or wrong password: same message for both. */
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleBadLogin(AuthenticationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
