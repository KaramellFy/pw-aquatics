package com.pwaquatics.shop.common.api;

import com.pwaquatics.shop.common.error.BusinessRuleException;
import com.pwaquatics.shop.common.error.ConflictException;
import com.pwaquatics.shop.common.error.ForbiddenException;
import com.pwaquatics.shop.common.error.LockedException;
import com.pwaquatics.shop.common.error.NotFoundException;
import com.pwaquatics.shop.common.error.RateLimitedException;
import com.pwaquatics.shop.common.error.ShopException;
import com.pwaquatics.shop.common.error.UnauthorizedException;
import com.pwaquatics.shop.common.web.RequestIdFilter;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 Problem Details response with a stable
 * {@code code} and a {@code traceId}. Never exposes stack traces or internals.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ShopException.class)
    ResponseEntity<Object> handleShopException(ShopException ex, WebRequest request) {
        HttpStatus status = switch (ex) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case BusinessRuleException ignored -> HttpStatus.UNPROCESSABLE_CONTENT;
            case RateLimitedException ignored -> HttpStatus.TOO_MANY_REQUESTS;
            case UnauthorizedException ignored -> HttpStatus.UNAUTHORIZED;
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            case LockedException ignored -> HttpStatus.LOCKED;
            default -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setProperty("code", ex.code());
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitedException limited) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, limited.retryAfter().toSeconds())));
        }
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @ExceptionHandler(CsrfException.class)
    ResponseEntity<Object> handleCsrf(CsrfException ex, WebRequest request) {
        return problem(ex, HttpStatus.FORBIDDEN, "CSRF_INVALID", "Missing or invalid CSRF token.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return problem(ex, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Object> handleAuthentication(AuthenticationException ex, WebRequest request) {
        return problem(ex, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("Unexpected error", ex);
        return problem(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiExceptionHandler::toFieldError)
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorResponse> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldErrorResponse(
                                result.getMethodParameter().getParameterName(),
                                toCode(error.getCodes() != null && error.getCodes().length > 0
                                        ? error.getCodes()[error.getCodes().length - 1]
                                        : "INVALID"),
                                error.getDefaultMessage())))
                .toList();
        return handleExceptionInternal(ex, validationProblem(errors), headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed request body.");
        problem.setProperty("code", "MALFORMED_REQUEST");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** Adds {@code code} (if missing) and {@code traceId} to every problem response. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", codeForStatus(statusCode));
            }
            String traceId = MDC.get(RequestIdFilter.MDC_KEY);
            if (traceId != null) {
                problem.setProperty("traceId", traceId);
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private ResponseEntity<Object> problem(
            Exception ex, HttpStatus status, String code, String detail, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }

    private static ProblemDetail validationProblem(List<FieldErrorResponse> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "One or more fields are invalid.");
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return problem;
    }

    private static FieldErrorResponse toFieldError(FieldError error) {
        return new FieldErrorResponse(error.getField(), toCode(error.getCode()), error.getDefaultMessage());
    }

    /** "NotBlank" → "NOT_BLANK". */
    static String toCode(String constraint) {
        if (constraint == null || constraint.isBlank()) {
            return "INVALID";
        }
        String simple = constraint.substring(constraint.lastIndexOf('.') + 1);
        return simple.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
    }

    private static String codeForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "MALFORMED_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 409 -> "CONFLICT";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "RATE_LIMITED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED";
        };
    }
}
