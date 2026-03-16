package com.finalProject.BookingMeetingRoom.common.exception;

import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ResponseCode responseCode;
    // start add data field
    private final Object data;
    // end add data field

    public CustomException(ResponseCode responseCode) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
        this.data = null;
    }

    // start add constructor with data
    public CustomException(ResponseCode responseCode, Object data) {
        super(responseCode.getMessage());
        this.responseCode = responseCode;
        this.data = data;
    }
    // end add constructor with data
}
