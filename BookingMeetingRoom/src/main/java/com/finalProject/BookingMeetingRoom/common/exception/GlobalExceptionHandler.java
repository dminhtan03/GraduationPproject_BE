package com.finalProject.BookingMeetingRoom.common.exception;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.finalProject.BookingMeetingRoom.common.payload.FieldViolation;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle generic exceptions.
     *
     * @param ex      the exception
     * @param request the web request
     * @return a standardized error response
     */
    // Client disconnected mid-response — harmless, suppress noisy stack trace
    @ExceptionHandler(org.springframework.web.context.request.async.AsyncRequestNotUsableException.class)
    public void handleAsyncDisconnect(Exception ex) {
        log.debug("Client disconnected: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGenericException(Exception ex, WebRequest request) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        return buildErrorResponse(ResponseCode.SYSTEM, request.getDescription(false));
    }

    /**
     * Handle AccessDeniedException (403 Forbidden).
     *
     * @param e       the exception
     * @param request the web request
     * @return a standardized error response
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Response<Void>> handleAccessDeniedException(
            org.springframework.security.access.AccessDeniedException e, WebRequest request) {
        return buildErrorResponse(e.getMessage(), HttpStatus.FORBIDDEN, request.getDescription(false), "403");
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Response<Void>> handleCustomException(CustomException ex, WebRequest request) {
        ResponseCode code = ex.getResponseCode();
        String message = (ex.getData() instanceof String) ? (String) ex.getData() : code.getMessage();
        return buildErrorResponse(message, code.getHttpStatus(), request.getDescription(false), code.getCode());
    }

    /**
     * Handle RuntimeException (400 Bad Request).
     *
     * @param e       the exception
     * @param request the web request
     * @return a standardized error response
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Response<Void>> handleRuntimeException(RuntimeException e, WebRequest request) {
        return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST, request.getDescription(false), "400");
    }

    /**
     * Handle NoResourceFoundException (404 Not Found for static resources).
     * This suppresses the common "No static resource ." warning in Spring Boot 3.
     *
     * @param e       the exception
     * @param request the web request
     * @return a standardized 404 response
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Response<Void>> handleNoResourceFoundException(NoResourceFoundException e, WebRequest request) {
        return buildErrorResponse("Resource not found: " + e.getResourcePath(), HttpStatus.NOT_FOUND, request.getDescription(false), "404");
    }

    /**
     * Handle validation errors (BindException).
     *
     * @param ex      the BindException
     * @param request the web request
     * @return a standardized error response
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Response<Void>> handleValidationException(BindException ex, WebRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::mapFieldErrorToViolation)
                .collect(Collectors.toList());

        Response<Void> response = new Response<>();
        Response.Metadata metadata = new Response.Metadata();
        metadata.setCode("400");
        metadata.setMessage("Validation failed");
        metadata.setErrors(violations);
        response.setMeta(metadata);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Build an error response.
     *
     * @param message the error message
     * @param status  the HTTP status
     * @param path    the request path
     * @param code    the internal error code
     * @return a ResponseEntity containing the error response
     */
    private ResponseEntity<Response<Void>> buildErrorResponse(String message, HttpStatus status, String path, String code) {
        Response<Void> response = new Response<>();
        Response.Metadata metadata = new Response.Metadata();
        metadata.setCode(code);
        metadata.setMessage(message);
        response.setMeta(metadata);

        return ResponseEntity.status(status).body(response);
    }

    private ResponseEntity<Response<Void>> buildErrorResponse(ResponseCode code, String path) {
        return buildErrorResponse(code.getMessage(), code.getHttpStatus(), path, code.getCode());
    }

    /**
     * Map FieldError to FieldViolation for validation errors.
     *
     * @param fieldError the FieldError
     * @return FieldViolation
     */
    private FieldViolation mapFieldErrorToViolation(FieldError fieldError) {
        return new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }
}