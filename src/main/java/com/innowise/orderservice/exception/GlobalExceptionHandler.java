package com.innowise.orderservice.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.innowise.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.orderservice.exception.conflict.OrderAlreadyCancelledException;
import com.innowise.orderservice.exception.conflict.RequestAlreadyProcessingException;
import com.innowise.orderservice.exception.integration.UserServiceUnavailableException;
import com.innowise.orderservice.exception.notfound.ResourceNotFoundException;
import com.innowise.orderservice.exception.security.ForbiddenException;
import com.innowise.orderservice.exception.security.SecurityContextException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    private ProblemDetail build(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);

        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId != null) {
            pd.setProperty(TRACE_ID_KEY, traceId);
        }

        return pd;
    }

    @ExceptionHandler(RequestAlreadyProcessingException.class)
    public ProblemDetail handleRequestAlreadyProcessing(RequestAlreadyProcessingException ex) {
        return build(HttpStatus.CONFLICT, "Request already processing", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFoundExceptions(ResourceNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(SecurityContextException.class)
    public ProblemDetail handleSecurityContextException(SecurityContextException ex) {
        return build(HttpStatus.UNAUTHORIZED, "Authentication error", ex.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AuthorizationDeniedException.class})
    public ProblemDetail handleForbidden(RuntimeException ex) {
        if (ex instanceof ForbiddenException fe) {
            return build(HttpStatus.FORBIDDEN, "Forbidden", fe.getMessage());
        }
        return build(HttpStatus.FORBIDDEN, "Forbidden", "Access denied");
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderStateException(InvalidOrderStateException ex) {
        return build(HttpStatus.CONFLICT, "Invalid order state", ex.getMessage());
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ProblemDetail handleOrderAlreadyCancelled(OrderAlreadyCancelledException ex) {
        return build(HttpStatus.CONFLICT, "Order already cancelled", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure", ex);
        return build(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "Resource was modified concurrently. Please reload and retry."
        );
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ProblemDetail handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "User service unavailable", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Validation error [traceId={}]: {}", traceId, ex.getMessage());

        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value"
                ))
                .toList();

        ProblemDetail pd = build(
                HttpStatus.BAD_REQUEST,
                "Validation failed",
                "Request validation failed"
        );
        pd.setProperty("violations", violations);
        return pd;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        if (ex.getRequiredType() != null) {
            message += ": expected " + ex.getRequiredType().getSimpleName();
        }
        if (ex.getValue() != null) {
            message += ", got '" + ex.getValue() + "'";
        }

        return build(HttpStatus.BAD_REQUEST, "Bad request", message);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllUncaughtException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        log.error("Unknown error occurred [traceId={}]", traceId, ex);

        return build(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                "An unexpected server error occurred"
        );
    }
}
