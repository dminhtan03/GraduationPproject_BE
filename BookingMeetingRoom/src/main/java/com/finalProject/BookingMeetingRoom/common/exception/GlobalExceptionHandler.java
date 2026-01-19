package com.finalProject.BookingMeetingRoom.common.exception;

import com.finalProject.BookingMeetingRoom.common.payload.FieldViolation;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.List;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle generic exceptions.
     *
     * @param ex      the exception
     * @param request the web request
     * @return a standardized error response
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response<Void>> handleGenericException(Exception ex, WebRequest request) {
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
        return buildErrorResponse(e.getMessage(), HttpStatus.FORBIDDEN, request.getDescription(false));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Response<Void>> handleCustomException(CustomException ex, WebRequest request) {
        ResponseCode code = ex.getResponseCode();
        return buildErrorResponse(code, request.getDescription(false));
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
        return buildErrorResponse(e.getMessage(), HttpStatus.BAD_REQUEST, request.getDescription(false));
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
     * @return a ResponseEntity containing the error response
     */
    private ResponseEntity<Response<Void>> buildErrorResponse(String message, HttpStatus status, String path) {
        Response<Void> response = new Response<>();
        Response.Metadata metadata = new Response.Metadata();
        metadata.setCode(String.valueOf(status.value()));
        metadata.setMessage(message);
        response.setMeta(metadata);

        return ResponseEntity.status(status).body(response);
    }

    /**
     * Build an error response.
     *

     * @param path    the request path
     * @return a ResponseEntity containing the error response
     */
    private ResponseEntity<Response<Void>> buildErrorResponse(ResponseCode code, String path) {
        Response<Void> response = new Response<>();
        Response.Metadata metadata = new Response.Metadata();
        metadata.setCode(code.getCode());
        metadata.setMessage(code.getMessage());
        response.setMeta(metadata);

        return ResponseEntity.status(code.getHttpStatus()).body(response);
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