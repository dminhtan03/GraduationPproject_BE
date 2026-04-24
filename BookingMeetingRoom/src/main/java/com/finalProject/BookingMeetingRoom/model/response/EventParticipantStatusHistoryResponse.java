package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantInviteStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái người tham gia)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventParticipantStatusHistoryResponse {
    private String id;
    private String action;
    private EventParticipantInviteStatus fromInviteStatus;
    private EventParticipantInviteStatus toInviteStatus;
    private EventParticipantCheckInStatus fromCheckInStatus;
    private EventParticipantCheckInStatus toCheckInStatus;
    private String note;
    private String changedByEmail;
    private LocalDateTime changedAt;
}
// end+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái người tham gia)

