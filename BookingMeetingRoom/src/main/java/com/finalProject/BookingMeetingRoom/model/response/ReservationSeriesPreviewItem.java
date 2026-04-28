package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng xem trước lịch đặt định kỳ (preview before confirm)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationSeriesPreviewItem {
    private LocalDate date;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean canBook;
    private String conflictReason; // ROOM_CONFLICT | USER_CONFLICT | ACADEMIC_SCHEDULE
}
// end+ chức năng xem trước lịch đặt định kỳ
