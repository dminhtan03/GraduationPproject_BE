package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.EventCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventResponse;

// start+ chức năng đặt phòng theo sự kiện (service)
public interface EventService {
    EventResponse createEvent(EventCreateRequest request, Authentication authentication);
    EventResponse getEventByReservationId(String reservationId, Authentication authentication);
    List<EventParticipantResponse> getParticipants(String eventId, Authentication authentication);
    EventParticipantResponse inviteParticipant(EventParticipantCreateRequest request, Authentication authentication);
    void removeParticipant(String participantId, Authentication authentication);
}
// end+ chức năng đặt phòng theo sự kiện (service)
