package com.drivers.shared.exception;

import com.drivers.config.TraceIdFilter;
import com.drivers.shared.exception.ex.*;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.*;

/**
 * Global Exception Handler for Sapremo "drivers" microservice
 *
 * Handles all exceptions and returns standardized error responses with:
 * - Consistent error codes
 * - Human-readable messages
 * - Field-level validation errors
 * - Trace IDs for debugging
 *
 * Error Response Format:
 * {
 *   "error": {
 *     "code": "error_code",
 *     "message": "Human readable message",
 *     "fields": { "fieldName": ["error1", "error2"] },
 *     "traceId": "uuid"
 *   }
 * }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Builds standardized error response with trace ID from MDC
     */
    private ErrorResponse buildError(String code, String message, Map<String, List<String>> fields) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        ErrorDetail detail = ErrorDetail.builder()
                .code(code)
                .message(message)
                .fields(fields)
                .traceId(traceId)
                .build();

        return new ErrorResponse(detail);
    }

    /**
     * Logs error with trace ID for debugging
     */
    private void logError(Exception ex, String traceId) {
        logger.error("Exception [traceId={}]: {}", traceId, ex.getMessage(), ex);
    }

    // ==================== AUTHENTICATION & AUTHORIZATION ====================

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("invalid_credentials", "Invalid username or password", null));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("invalid_credentials", ex.getMessage(), null));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientAuth(InsufficientAuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("authentication_required", "Full authentication is required to access this resource", null));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError("authentication_failed", "Authentication failed: " + ex.getMessage(), null));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError("access_denied", "You do not have permission to access this resource", null));
    }

    @ExceptionHandler(ClientAbortException.class)
    public void handleClientAbortException(ClientAbortException ex) {
        logger.warn("Client aborted connection before response was fully sent (Broken pipe).");
    }

    // ==================== BUSINESS LOGIC EXCEPTIONS ====================

    @ExceptionHandler(DriverNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDriverNotFound(DriverNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("driver_not_found", ex.getMessage(), null));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("order_not_found", ex.getMessage(), null));
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("payment_not_found", ex.getMessage(), null));
    }

    @ExceptionHandler(ReturnNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleReturnNotFound(ReturnNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("return_not_found", ex.getMessage(), null));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("entity_not_found", ex.getMessage(), null));
    }

    @ExceptionHandler(PhoneAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePhoneAlreadyExists(PhoneAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("phone_already_exists", ex.getMessage(), null));
    }

    @ExceptionHandler(CarNumberAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCarNumberAlreadyExists(CarNumberAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("car_number_already_exists", ex.getMessage(), null));
    }

    @ExceptionHandler(NegativeDebtException.class)
    public ResponseEntity<ErrorResponse> handleNegativeDebt(NegativeDebtException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("negative_debt", ex.getMessage(), null));
    }

    @ExceptionHandler(DriverDebtNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDriverDebtNotFound(DriverDebtNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("debt_not_found", ex.getMessage(), null));
    }


    // ==================== VALIDATION EXCEPTIONS ====================

    /**
     * Handles @Valid annotation validation failures in request body
     * Returns field-level validation errors for frontend to display
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fields = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fields.computeIfAbsent(error.getField(), k -> new ArrayList<>())
                    .add(error.getDefaultMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("validation_error", "Validation failed for one or more fields", fields));
    }

    /**
     * Handles constraint violations (e.g., @NotNull, @Size, etc.)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> fields = new HashMap<>();

        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            String fieldName = violation.getPropertyPath().toString();
            fields.computeIfAbsent(fieldName, k -> new ArrayList<>())
                    .add(violation.getMessage());
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("constraint_violation", "Constraint validation failed", fields));
    }

    /**
     * Handles missing required request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());

        Map<String, List<String>> fields = new HashMap<>();
        fields.put(ex.getParameterName(), List.of("Parameter is required"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("missing_parameter", message, fields));
    }

    /**
     * Handles missing required request headers
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        String message = String.format("Required header '%s' is missing", ex.getHeaderName());

        Map<String, List<String>> fields = new HashMap<>();
        fields.put(ex.getHeaderName(), List.of("Header is required"));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("missing_header", message, fields));
    }

    /**
     * Handles type mismatch in request parameters
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = String.format("Parameter '%s' must be of type %s",
                ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");

        Map<String, List<String>> fields = new HashMap<>();
        fields.put(ex.getName(), List.of(message));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("type_mismatch", message, fields));
    }

    /**
     * Handles malformed JSON in request body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Malformed JSON request";
        if (ex.getMessage() != null && ex.getMessage().contains("JSON parse error")) {
            message = "Invalid JSON format: " + ex.getMessage().split(":")[0];
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("malformed_json", message, null));
    }

    // ==================== DATABASE EXCEPTIONS ====================

    /**
     * Handles database constraint violations (unique, foreign key, etc.)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String message = "Database constraint violation";

        if (ex.getMessage() != null) {
            if (ex.getMessage().contains("unique") || ex.getMessage().contains("duplicate")) {
                message = "A record with this value already exists";
            } else if (ex.getMessage().contains("foreign key")) {
                message = "Referenced record does not exist";
            } else if (ex.getMessage().contains("not-null")) {
                message = "Required field is missing";
            }
        }

        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        logError(ex, traceId);

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("data_integrity_violation", message, null));
    }

    /**
     * Handles optimistic locking failures (concurrent modification)
     */
    @ExceptionHandler(OptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("concurrent_modification",
                        "The resource was modified by another user. Please refresh and try again", null));
    }

    /**
     * Handles general persistence exceptions
     */
    @ExceptionHandler(PersistenceException.class)
    public ResponseEntity<ErrorResponse> handlePersistence(PersistenceException ex) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        logError(ex, traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("database_error", "A database error occurred. Please try again", null));
    }

    // ==================== HTTP & ROUTING EXCEPTIONS ====================

    /**
     * Handles 404 - endpoint not found
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFound(NoHandlerFoundException ex) {
        String message = String.format("Endpoint not found: %s %s", ex.getHttpMethod(), ex.getRequestURL());

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("endpoint_not_found", message, null));
    }

    /**
     * Handles 404 - static resource not found
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        String message = String.format("Resource not found: %s %s", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError("resource_not_found", message, null));
    }

    /**
     * Handles 405 - method not allowed
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        String message = String.format("Method '%s' is not supported for this endpoint. Supported methods: %s",
                ex.getMethod(),
                String.join(", ", Objects.requireNonNull(ex.getSupportedMethods())));

        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(buildError("method_not_allowed", message, null));
    }

    // ==================== GENERIC EXCEPTIONS ====================

    /**
     * Handles IllegalArgumentException (usually from business logic)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError("invalid_argument", ex.getMessage(), null));
    }

    /**
     * Handles IllegalStateException (usually from business logic)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(buildError("invalid_state", ex.getMessage(), null));
    }

    /**
     * Catches all other unhandled exceptions
     * Logs full stack trace and returns generic error to user
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }

        logError(ex, traceId);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError("internal_server_error",
                        "An unexpected error occurred. Please contact support with trace ID: " + traceId, null));
    }
}
