package com.innowise.internship.orderservice.exception;

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

import com.innowise.internship.orderservice.exception.conflict.InvalidOrderStateException;
import com.innowise.internship.orderservice.exception.conflict.OrderAlreadyCancelledException;
import com.innowise.internship.orderservice.exception.conflict.RequestAlreadyProcessingException;
import com.innowise.internship.orderservice.exception.integration.UserServiceUnavailableException;
import com.innowise.internship.orderservice.exception.notfound.ResourceNotFoundException;
import com.innowise.internship.orderservice.exception.security.SecurityContextException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RequestAlreadyProcessingException.class)
    public ProblemDetail handleRequestAlreadyProcessing(RequestAlreadyProcessingException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFoundExceptions(ResourceNotFoundException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problemDetail.setTitle("Resource not found");

        return problemDetail;
    }

    @ExceptionHandler(SecurityContextException.class)
    public ProblemDetail handleSecurityContextException(SecurityContextException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problemDetail.setTitle("Authentication error");

        return problemDetail;
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        problemDetail.setTitle("Forbidden");
        return problemDetail;
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ProblemDetail handleInvalidOrderStateException(InvalidOrderStateException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Invalid order state");

        return problemDetail;
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ProblemDetail handleOrderAlreadyCancelled(OrderAlreadyCancelledException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problemDetail.setTitle("Order already cancelled");
        return problemDetail;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Optimistic lock failure: {}", ex.getMessage());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Resource was modified concurrently. Please reload and retry."
        );
        problemDetail.setTitle("Concurrent modification");
        return problemDetail;
    }

    @ExceptionHandler(UserServiceUnavailableException.class)
    public ProblemDetail handleUserServiceUnavailable(UserServiceUnavailableException ex) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage()
        );
        problemDetail.setTitle("User service unavailable");
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String traceId = MDC.get(TRACE_ID_KEY);
        log.warn("Validation error [traceId={}]: {}", traceId, ex.getMessage());

        ProblemDetail problemDetail = ex.getBody();
        problemDetail.setTitle("Request validation failed");

        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> Map.of(
                        "field", err.getField(),
                        "message", err.getDefaultMessage() != null ? err.getDefaultMessage() : "Invalid value"
                ))
                .toList();

        problemDetail.setProperty("violations", violations);
        if (traceId != null) {
            problemDetail.setProperty(TRACE_ID_KEY, traceId);
        }

        return problemDetail;
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

        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleAllUncaughtException(Exception ex) {
        String traceId = MDC.get(TRACE_ID_KEY);

        log.error("Unknown error occurred [traceId={}]", traceId, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred"
        );
        problemDetail.setTitle("Internal server error");

        if (traceId != null) {
            problemDetail.setProperty(TRACE_ID_KEY, traceId);
        }

        return problemDetail;
    }
}
