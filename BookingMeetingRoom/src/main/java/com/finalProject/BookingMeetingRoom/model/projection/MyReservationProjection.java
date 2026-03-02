package com.finalProject.BookingMeetingRoom.model.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface MyReservationProjection {
    String getReservationId();
    String getLocationCode();
    String getBuildingName();
    String getFloorName();
    String getAddress();
    String getReservationStatus();
    LocalDate getSelectedDate();
    LocalDateTime getStartTime();
    LocalDateTime getEndTime();
    Double getDuration();
    Long getIsFeedback();
}
