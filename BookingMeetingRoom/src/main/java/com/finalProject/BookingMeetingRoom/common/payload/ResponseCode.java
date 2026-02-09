package com.finalProject.BookingMeetingRoom.common.payload;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ResponseCode {

    // ======= System Errors =======
    SYSTEM("ERR_501", "System error. Please try again later!", HttpStatus.INTERNAL_SERVER_ERROR),
    INTERNAL_SERVER_ERROR("ERR_500", "Internal server error", HttpStatus.INTERNAL_SERVER_ERROR),
    NO_CODE("ERR_000", "No error code specified", HttpStatus.INTERNAL_SERVER_ERROR),
    CACHE_FAILED("VAL_500", "Cache failed" , HttpStatus.INTERNAL_SERVER_ERROR),

    // ======= User Errors =======
    USER_NOT_FOUND("USR_404", "User not found", HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_EXISTS("USR_409", "Email already exists", HttpStatus.CONFLICT),
    INVALID_CURRENT_PASSWORD("USR_401", "Invalid current password", HttpStatus.UNAUTHORIZED),
    INVALID_PASSWORD("USR_401", "Invalid password", HttpStatus.UNAUTHORIZED),
    PASSWORD_CONFIRM_NOT_MATCH("USR_400", "New password and confirm password do not match", HttpStatus.BAD_REQUEST),
    USER_DISABLE("USR_401", "User disabled" , HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("USR_401", "Account locked" , HttpStatus.UNAUTHORIZED),
    // ==========Room Errors===========
    ROOM_NOT_FOUND("SEAT_404", "Seat not found" , HttpStatus.NOT_FOUND),
    CANNOT_RESERVE_ROOM("SEAT_410","Seat cannot be reserve at selected period",HttpStatus.BAD_REQUEST),
    // ======= Validation Errors =======
    VALIDATION_FAILED("VAL_422", "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),
    // ======= OTP Errors =======
    INVALID_OTP("OTP_400", "Invalid OTP", HttpStatus.BAD_REQUEST),
    EXPIRED_OTP("OTP_401", "OTP has expired", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED("OTP_409", "OTP has already been used", HttpStatus.BAD_REQUEST),

    // ======= Role Errors =======
    ROLE_NOT_FOUND("ROL_404", "Role not found", HttpStatus.NOT_FOUND),

    // ======= Token Errors =======
    TOKEN_BLACKLISTED("TOK_403", "Token is blacklisted", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("TOK_403", "Access denied" , HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_NOT_FOUND("TOK_404", "Refresh token not found" , HttpStatus.NOT_FOUND ),
    // ======= Reservation Errors =======
    USER_TIME_OVERLAP("USR_409", "User has already reserved a seat at this time", HttpStatus.CONFLICT),

    // ======= Other Errors =======
    AUTH_HEADER_NOT_FOUND("AUTH_401", "Authorization header not found", HttpStatus.UNAUTHORIZED);



    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ResponseCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}