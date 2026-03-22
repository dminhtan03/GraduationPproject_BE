package com.finalProject.BookingMeetingRoom.common.enums;

import lombok.Getter;

@Getter
public enum EmailTemplateName {

    ACTIVATE_ACCOUNT("activate_account"),
    RESERVATION_STATUS("reservation-status"),
    FORCE_CANCEL("force_cancel");

    private final String name;
    EmailTemplateName(String name) {
        this.name = name;
    }

}
