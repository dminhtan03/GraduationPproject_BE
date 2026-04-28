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

// start+ chức năng đặt phòng theo sự kiện (EventParticipant entity)
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_event_participant")
public class EventParticipant {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "EVENT_ID", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID")
    private User user;

    @Column(name = "EMAIL")
    private String email;

    @Column(name = "FULL_NAME")
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "INVITE_STATUS")
    private EventParticipantInviteStatus inviteStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHECKIN_STATUS")
    private EventParticipantCheckInStatus checkInStatus;

    @Column(name = "CHECKIN_TIME")
    private LocalDateTime checkInTime;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
// end+ chức năng đặt phòng theo sự kiện (EventParticipant entity)
