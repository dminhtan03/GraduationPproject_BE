package com.finalProject.BookingMeetingRoom.common.exception;

import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ResponseCode responseCode;

    public CustomException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
    }

}
