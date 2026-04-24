package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.model.request.EventCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantRespondRequest;
import com.finalProject.BookingMeetingRoom.model.response.AdminEventResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantStatusHistoryResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventResponse;

// start+ chức năng đặt phòng theo sự kiện (service)
public interface EventService {
    Page<AdminEventResponse> getAllEventsForAdmin(int page, int size, Authentication authentication);
    EventResponse createEvent(EventCreateRequest request, Authentication authentication);
    EventResponse getEventByReservationId(String reservationId, Authentication authentication);
    List<EventParticipantResponse> getParticipants(String eventId, Authentication authentication);
    EventParticipantResponse inviteParticipant(EventParticipantCreateRequest request, Authentication authentication);
    void removeParticipant(String participantId, Authentication authentication);

    // start+ chức năng đặt phòng theo sự kiện (người tham gia phản hồi + lịch sử trạng thái)
    EventParticipantResponse respondInvitation(String participantId, EventParticipantRespondRequest request, Authentication authentication);
    List<EventParticipantStatusHistoryResponse> getParticipantHistory(String participantId, Authentication authentication);
    // end+ chức năng đặt phòng theo sự kiện (người tham gia phản hồi + lịch sử trạng thái)

    // start+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)
    List<EventParticipantResponse> getMyInvitations(Authentication authentication);
    // end+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)
}
// end+ chức năng đặt phòng theo sự kiện (service)
