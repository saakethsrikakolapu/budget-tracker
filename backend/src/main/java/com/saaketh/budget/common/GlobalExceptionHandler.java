package com.saaketh.budget.common;

import com.saaketh.budget.account.AccountNameTakenException;
import com.saaketh.budget.auth.EmailAlreadyRegisteredException;
import com.saaketh.budget.auth.InvalidPasswordException;
import com.saaketh.budget.imports.InvalidUploadException;
import com.saaketh.budget.imports.parser.StatementParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

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

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(AccountNameTakenException.class)
    ProblemDetail handleAccountNameTaken(AccountNameTakenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setProperty("errors", Map.of("name", e.getMessage()));
        return problem;
    }

    @ExceptionHandler(InvalidUploadException.class)
    ProblemDetail handleInvalidUpload(InvalidUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** The statement had bad rows; "rowErrors" lists each one, e.g. "Line 7: ...". */
    @ExceptionHandler(StatementParseException.class)
    ProblemDetail handleStatementParse(StatementParseException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The file could not be imported. Nothing was saved.");
        problem.setProperty("rowErrors", e.getErrors());
        return problem;
    }

    /** Rejected by Spring before reaching our code (spring.servlet.multipart.max-file-size). */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE, "The file is larger than 2 MB.");
    }

    /** A required form field or file is missing, or accountId isn't a number. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            MethodArgumentTypeMismatchException.class})
    ProblemDetail handleBadParameters(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Missing or invalid request fields");
    }
}
