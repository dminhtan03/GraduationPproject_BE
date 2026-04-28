package com.finalProject.BookingMeetingRoom.model.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng đặt phòng lặp lại (create series request)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeriesCreateRequest {
    @NotNull
    private String roomId;

    @NotNull
    private LocalTime startTimeOfDay;

    @NotNull
    private LocalTime endTimeOfDay;

    @NotNull
    private List<String> daysOfWeek;

    @NotNull
    private LocalDate fromDate;

    private LocalDate untilDate;

    private Integer rollingWindowWeeks;

    @NotBlank
    private String purpose;

    private String note;
}
// end+ chức năng đặt phòng lặp lại (create series request)
