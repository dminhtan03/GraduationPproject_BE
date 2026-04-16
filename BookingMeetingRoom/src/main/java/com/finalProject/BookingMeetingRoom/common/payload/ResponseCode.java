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
    USER_NOT_FOUND("USR_404", "Invalid email", HttpStatus.NOT_FOUND),
    USER_TIME_EXCEEDED("USR_409", "User reached time limit a day", HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS("USR_409", "Email already exists", HttpStatus.CONFLICT),
    INVALID_CURRENT_PASSWORD("USR_401", "Invalid current password", HttpStatus.UNAUTHORIZED),
    INVALID_PASSWORD("USR_401", "Invalid password", HttpStatus.NOT_FOUND),
    PASSWORD_CONFIRM_NOT_MATCH("USR_400", "New password and confirm password do not match", HttpStatus.BAD_REQUEST),
    USER_DISABLE("USR_401", "User disabled" , HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("USR_401", "Account locked" , HttpStatus.UNAUTHORIZED),
    ALREADY_ACTIVATED("USR_409", "Account already activated", HttpStatus.CONFLICT),
    PERMISSION_DENIED("USR_403", "Have permission to action", HttpStatus.FORBIDDEN),
    // start add booking locked error
    BOOKING_FUNCTION_LOCKED("USR_423", "Booking function is locked", HttpStatus.LOCKED),
    // end add booking locked error

    // ======= Validation Errors =======
    VALIDATION_FAILED("VAL_422", "Validation failed", HttpStatus.UNPROCESSABLE_ENTITY),

    // ======= OTP Errors =======
    INVALID_OTP("OTP_400", "Invalid OTP", HttpStatus.BAD_REQUEST),
    EXPIRED_OTP("OTP_401", "OTP has expired", HttpStatus.BAD_REQUEST),
    OTP_ALREADY_USED("OTP_409", "OTP has already been used", HttpStatus.BAD_REQUEST),

    // ======= Role Errors =======
    ROLE_NOT_FOUND("ROL_404", "Role not found", HttpStatus.NOT_FOUND),

    // ======= Token Errors =======
    TOKEN_INVALID("TOK_401", "Token invalid" , HttpStatus.UNAUTHORIZED),
    TOKEN_BLACKLISTED("TOK_403", "Token is blacklisted", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("TOK_403", "Access denied" , HttpStatus.FORBIDDEN),
    REFRESH_TOKEN_NOT_FOUND("TOK_404", "Refresh token not found" , HttpStatus.NOT_FOUND ),

    // ======= ROOM Errors =======
    ROOM_NOT_FOUND("ROOM_404", "ROOM not found" , HttpStatus.NOT_FOUND),
    ROOM_NOT_UNAVAILABLE("ROOM_400", "ROOM not unavailable" , HttpStatus.BAD_REQUEST),
    ROOM_NOT_AVAILABLE("ROOM_400", "ROOM is not available" , HttpStatus.BAD_REQUEST),
    CANNOT_CHANGE_ROOM_STATUS("ROOM_409", "ROOM can be changed only if it is broken", HttpStatus.CONFLICT),
    ROOM_BROKEN("ROOM_409", "ROOM is broken and cannot be used", HttpStatus.CONFLICT),
    ROOM_ALREADY_EXISTS("ROOM_409", "Room with this location code already exists on this floor", HttpStatus.CONFLICT),
    CANNOT_RESERVE_ROOM("ROOM_410","Someone has already booked a room during your chosen time slot.",HttpStatus.BAD_REQUEST),
    ROOM_IN_ACADEMIC_SCHEDULE("ROOM_411", "Phòng đang trong thời gian học cố định", HttpStatus.BAD_REQUEST),
    ACADEMIC_SCHEDULE_OVERLAP("ACD_409", "Lịch học cố định bị trùng khung thời gian cho phòng này", HttpStatus.CONFLICT),

    // ======= Reservation Errors =======
    RESERVATION_NOT_FOUND("RES_404", "Reservation not found" , HttpStatus.NOT_FOUND),
    RESERVATION_INVALID_STATUS("RES_400", "Reservation not found or status of reservation is not RESERVED"
            , HttpStatus.BAD_REQUEST),
    RESERVATION_NOT_IN_USE("RES_400", "Reservation not in use", HttpStatus.BAD_REQUEST),
    RESERVATION_USER_NOT_FOUND("RES_404", "You are not the owner of this reservation" ,
            HttpStatus.NOT_FOUND),
    RESERVATION_NOT_EXTEND_AFTER_MIDNIGHT("RES_400", "Cannot extend hour in midnight", HttpStatus.BAD_REQUEST),
    RESERVATION_TIME_OVERLAP("RES_409", "Overlap with other reservations", HttpStatus.CONFLICT),
    RESERVATION_NOT_RESERVED("RES_400", "This Reservation is not reserved" , HttpStatus.BAD_REQUEST),
    RESERVATION_NOT_TIME_CHECK_IN("RES_400", "You cannot check in before start time." , HttpStatus.BAD_REQUEST),
    // start add reservation check-in expired error
    RESERVATION_CHECK_IN_EXPIRED("RES_400", "Check-in time has expired (15 minutes limit).", HttpStatus.BAD_REQUEST),
    // end add reservation check-in expired error
    RESERVATION_INVALID_HOUR("RES_400", "Hour is invalid", HttpStatus.BAD_REQUEST),
    RESERVATION_HISTORY_NOT_FOUND("RES_HIS_404", "Reservation history not found", HttpStatus.NOT_FOUND),
    FEEDBACK_ONLY_FOR_COMPLETED_RESERVATION("FBD_400", "Feedback can only be submitted for completed reservations", HttpStatus.BAD_REQUEST),

    USER_TIME_OVERLAP("USR_409", "User has already reserved a ROOM at this time", HttpStatus.CONFLICT),

    // ======= Notification Errors =======
    NOTIFICATION_NOT_FOUND("NOTI_404", "Notification not found", HttpStatus.NOT_FOUND),

    // ======= Building Errors =======
    BUILDING_NOT_FOUND("BLD_404", "Building not found", HttpStatus.NOT_FOUND),

    // ======= Floor Errors =======
    FLOOR_NOT_FOUND("FlR_404", "Floor not found", HttpStatus.NOT_FOUND),

    // ======= Lock =======
    OPTIMISTIC_LOCK_ERROR("LOCK_500", "Optimistic lock error", HttpStatus.INTERNAL_SERVER_ERROR),

    // ======= Other Errors =======
    AUTH_HEADER_NOT_FOUND("AUTH_401", "Authorization header not found", HttpStatus.UNAUTHORIZED),
    FEEDBACK_ALREADY_EXISTS("FBD_409", "Feedback already exists for this reservation", HttpStatus.CONFLICT),
    CANNOT_FORCE_RETURN("RTN_401","ROOM cannot be force returned",HttpStatus.CONFLICT),
    INVALID_DATE_FORMAT("DATE_400", "Invalid date format", HttpStatus.BAD_REQUEST),

    // ======= Amenity Errors =======
    AMENITY_ALREADY_EXISTS("AMN_409", "Amenity with this name already exists", HttpStatus.CONFLICT),
    AMENITY_NOT_FOUND("AMN_404", "Amenity not found", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ResponseCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}