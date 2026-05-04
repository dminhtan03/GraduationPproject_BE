package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantInviteStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng đặt phòng theo sự kiện (lịch sử thay đổi trạng thái người tham gia)
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_event_participant_status_history")
public class EventParticipantStatusHistory {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EVENT_PARTICIPANT_ID", nullable = false)
    private EventParticipant participant;

    @Column(name = "ACTION", nullable = false)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "FROM_INVITE_STATUS")
    private EventParticipantInviteStatus fromInviteStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "TO_INVITE_STATUS")
    private EventParticipantInviteStatus toInviteStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "FROM_CHECKIN_STATUS")
    private EventParticipantCheckInStatus fromCheckInStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "TO_CHECKIN_STATUS")
    private EventParticipantCheckInStatus toCheckInStatus;

    @Column(name = "NOTE")
    private String note;

    @Column(name = "CHANGED_BY_EMAIL")
    private String changedByEmail;

    @Column(name = "CHANGED_AT", nullable = false)
    private LocalDateTime changedAt;
}
// end+ chức năng đặt phòng theo sự kiện (lịch sử thay đổi trạng thái người tham gia)

