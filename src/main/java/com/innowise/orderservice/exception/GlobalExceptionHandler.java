package com.innowise.orderservice.exception;

import java.util.List;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
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

    private static ResponseEntity<ProblemDetail> toResponse(HttpStatus status, ProblemDetail body) {
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(RequestAlreadyProcessingException.class)
    public ResponseEntity<ProblemDetail> handleRequestAlreadyProcessing(RequestAlreadyProcessingException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return toResponse(status, build(status, "Request already processing", ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFoundExceptions(ResourceNotFoundException ex) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        return toResponse(status, build(status, "Resource not found", ex.getMessage()));
    }

    @ExceptionHandler(SecurityContextException.class)
    public ResponseEntity<ProblemDetail> handleSecurityContextException(SecurityContextException ex) {
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        return toResponse(status, build(status, "Authentication error", ex.getMessage()));
    }

    @ExceptionHandler({ForbiddenException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ProblemDetail> handleForbidden(RuntimeException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        if (ex instanceof ForbiddenException fe) {
            return toResponse(status, build(status, "Forbidden", fe.getMessage()));
        }
        return toResponse(status, build(status, "Forbidden", "Access denied"));
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<ProblemDetail> handleInvalidOrderStateException(InvalidOrderStateException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return toResponse(status, build(status, "Invalid order state", ex.getMessage()));
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ResponseEntity<ProblemDetail> handleOrderAlreadyCancelled(OrderAlreadyCancelledException ex) {
        HttpStatus status = HttpStatus.CONFLICT;
        return toResponse(status, build(status, "Order already cancelled", ex.getMessage()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure", ex);
        HttpStatus status = HttpStatus.CONFLICT;
        return toResponse(
                status,
                build(
                        status,
                        "Concurrent modification",
                        "Resource was modified concurrently. Please reload and retry."
                )
        );
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        return toResponse(status, build(status, "User service unavailable", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Validation error [traceId={}]: {}", traceId, ex.getMessage());

        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value"
                ))
                .toList();

        HttpStatus status = HttpStatus.BAD_REQUEST;
        ProblemDetail pd = build(
                status,
                "Validation failed",
                "Request validation failed"
        );
        pd.setProperty("violations", violations);
        return toResponse(status, pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return toResponse(status, build(status, "Bad request", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value for parameter '" + ex.getName() + "'";
        if (ex.getRequiredType() != null) {
            message += ": expected " + ex.getRequiredType().getSimpleName();
        }
        if (ex.getValue() != null) {
            message += ", got '" + ex.getValue() + "'";
        }

        HttpStatus status = HttpStatus.BAD_REQUEST;
        return toResponse(status, build(status, "Bad request", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleAllUncaughtException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        log.error("Unknown error occurred [traceId={}]", traceId, ex);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return toResponse(
                status,
                build(
                        status,
                        "Internal server error",
                        "An unexpected server error occurred"
                )
        );
    }
}
