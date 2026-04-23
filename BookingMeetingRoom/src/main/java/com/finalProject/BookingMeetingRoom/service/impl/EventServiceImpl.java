package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantInviteStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Event;
import com.finalProject.BookingMeetingRoom.model.entity.EventParticipant;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.EventCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantCreateRequest;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventResponse;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantRepository;
import com.finalProject.BookingMeetingRoom.repository.EventRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EventService;

import lombok.RequiredArgsConstructor;

// start+ chức năng đặt phòng theo sự kiện (invite-only participants + check-in list)
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
    }

    private void assertOwnerOrAdmin(Reservation reservation, Authentication authentication) {
        if (reservation == null) {
            throw new CustomException(ResponseCode.RESERVATION_NOT_FOUND);
        }
        if (authentication == null || authentication.getName() == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }
        if (isAdmin(authentication)) return;
        if (!reservation.getUser().getUsername().equalsIgnoreCase(authentication.getName())) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }
    }

    private EventResponse toEventResponse(Event event, List<EventParticipantResponse> participants) {
        return EventResponse.builder()
                .id(event.getId())
                .reservationId(event.getReservation().getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .visibility(event.getVisibility())
                .createdAt(event.getCreatedAt())
                .participants(participants)
                .build();
    }

    private EventParticipantResponse toParticipantResponse(EventParticipant participant) {
        return EventParticipantResponse.builder()
                .id(participant.getId())
                .userId(participant.getUser() != null ? participant.getUser().getId() : null)
                .email(participant.getUser() != null ? participant.getUser().getUsername() : participant.getEmail())
                .fullName(participant.getUser() != null && participant.getUser().getUserInfo() != null
                        ? participant.getUser().getUserInfo().getFullName()
                        : participant.getFullName())
                .inviteStatus(participant.getInviteStatus())
                .checkInStatus(participant.getCheckInStatus())
                .checkInTime(participant.getCheckInTime())
                .build();
    }

    @Override
    @Transactional
    public EventResponse createEvent(EventCreateRequest request, Authentication authentication) {
        Reservation reservation = reservationRepository.findById(request.getReservationId())
                .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));
        assertOwnerOrAdmin(reservation, authentication);

        if (eventRepository.findByReservation_Id(reservation.getId()).isPresent()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Event already exists for this reservation");
        }

        Event event = new Event();
        event.setId(UUID.randomUUID().toString());
        event.setReservation(reservation);
        event.setTitle(request.getTitle().trim());
        event.setDescription(request.getDescription());
        event.setVisibility(request.getVisibility());
        event.setCreatedAt(LocalDateTime.now());
        event.setUpdatedAt(LocalDateTime.now());
        eventRepository.save(event);

        return toEventResponse(event, List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventByReservationId(String reservationId, Authentication authentication) {
        Event event = eventRepository.findByReservation_Id(reservationId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Event not found"));

        assertOwnerOrAdmin(event.getReservation(), authentication);

        List<EventParticipantResponse> participants = eventParticipantRepository.findByEvent_Id(event.getId()).stream()
                .map(this::toParticipantResponse)
                .toList();

        return toEventResponse(event, participants);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EventParticipantResponse> getParticipants(String eventId, Authentication authentication) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Event not found"));
        assertOwnerOrAdmin(event.getReservation(), authentication);

        return eventParticipantRepository.findByEvent_Id(eventId).stream()
                .map(this::toParticipantResponse)
                .toList();
    }

    @Override
    @Transactional
    public EventParticipantResponse inviteParticipant(EventParticipantCreateRequest request, Authentication authentication) {
        Event event = eventRepository.findById(request.getEventId())
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Event not found"));
        assertOwnerOrAdmin(event.getReservation(), authentication);

        boolean hasUserId = request.getUserId() != null && !request.getUserId().isBlank();
        boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();
        if (!hasUserId && !hasEmail) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "userId or email is required");
        }

        User user = null;
        String email = null;
        String fullName = null;

        if (hasUserId) {
            user = userRepository.findById(request.getUserId())
                    .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
            if (eventParticipantRepository.findByEvent_IdAndUser_Id(event.getId(), user.getId()).isPresent()) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED, "User already invited");
            }
        } else {
            final String emailValue = request.getEmail().trim();
            boolean existsByEmail = eventParticipantRepository.findByEvent_Id(event.getId()).stream()
                    .anyMatch(p -> p.getEmail() != null && p.getEmail().equalsIgnoreCase(emailValue));
            if (existsByEmail) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED, "Email already invited");
            }
            email = emailValue;
            fullName = request.getFullName();
        }

        EventParticipant participant = new EventParticipant();
        participant.setId(UUID.randomUUID().toString());
        participant.setEvent(event);
        participant.setUser(user);
        participant.setEmail(email);
        participant.setFullName(fullName);
        participant.setInviteStatus(EventParticipantInviteStatus.INVITED);
        participant.setCheckInStatus(EventParticipantCheckInStatus.NOT_CHECKED_IN);
        participant.setCreatedAt(LocalDateTime.now());
        participant.setUpdatedAt(LocalDateTime.now());

        eventParticipantRepository.save(participant);
        return toParticipantResponse(participant);
    }

    @Override
    @Transactional
    public void removeParticipant(String participantId, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));
        assertOwnerOrAdmin(participant.getEvent().getReservation(), authentication);
        eventParticipantRepository.delete(participant);
    }
}
// end+ chức năng đặt phòng theo sự kiện (invite-only participants + check-in list)
