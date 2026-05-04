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
import com.finalProject.BookingMeetingRoom.model.entity.EventParticipantStatusHistory;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.request.EventCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantRespondRequest;
import com.finalProject.BookingMeetingRoom.model.response.AdminEventResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventParticipantStatusHistoryResponse;
import com.finalProject.BookingMeetingRoom.model.response.EventResponse;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantRepository;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantStatusHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.EventRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.EventService;
import com.finalProject.BookingMeetingRoom.service.NotificationService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

// start+ chức năng đặt phòng theo sự kiện (invite-only participants + check-in list)
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventParticipantStatusHistoryRepository eventParticipantStatusHistoryRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> {
                    String auth = a.getAuthority();
                    return "ROLE_ADMIN".equalsIgnoreCase(auth) || "ADMIN".equalsIgnoreCase(auth) || "ROLE_ADMIN".equalsIgnoreCase("ROLE_" + auth);
                });
    }

    private User getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND));
    }

    private void assertOwnerOrAdmin(Reservation reservation, Authentication authentication) {
        if (reservation == null) {
            throw new CustomException(ResponseCode.RESERVATION_NOT_FOUND);
        }
        if (isAdmin(authentication)) return;
        User currentUser = getCurrentUser(authentication);
        if (reservation.getUser() == null || !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }
    }

    // start+ chức năng đặt phòng theo sự kiện (participant cũng xem được event booking)
    private void assertOwnerOrAdminOrParticipant(Event event, Authentication authentication) {
        if (event == null || event.getReservation() == null) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Event not found");
        }
        if (isAdmin(authentication)) return;

        User currentUser = getCurrentUser(authentication);
        Reservation reservation = event.getReservation();
        if (reservation.getUser() != null && reservation.getUser().getId().equals(currentUser.getId())) {
            return;
        }

        boolean isParticipant = eventParticipantRepository.findByEvent_IdAndUser_Id(event.getId(), currentUser.getId()).isPresent();
        if (!isParticipant) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }
    }
    // end+ chức năng đặt phòng theo sự kiện (participant cũng xem được event booking)

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
        String email = participant.getEmail();
        if (participant.getUser() != null && participant.getUser().getUserInfo() != null) {
            email = participant.getUser().getUserInfo().getEmail();
        }

        Event event = participant.getEvent();
        Reservation reservation = event != null ? event.getReservation() : null;
        var room = reservation != null ? reservation.getRoom() : null;
        String roomLocationCode = room != null ? room.getLocationCode() : null;
        String roomAddress = null;
        if (room != null && room.getFloor() != null && room.getFloor().getBuilding() != null) {
            var building = room.getFloor().getBuilding();
            roomAddress = building.getAddress() != null ? building.getAddress() : building.getName();
        }

        return EventParticipantResponse.builder()
                .id(participant.getId())
                .userId(participant.getUser() != null ? participant.getUser().getId() : null)
                .email(email)
                .fullName(participant.getUser() != null && participant.getUser().getUserInfo() != null
                        ? participant.getUser().getUserInfo().getFullName()
                        : participant.getFullName())
                .inviteStatus(participant.getInviteStatus())
                .checkInStatus(participant.getCheckInStatus())
                .checkInTime(participant.getCheckInTime())
                .eventId(event != null ? event.getId() : null)
                .reservationId(reservation != null ? reservation.getId() : null)
                .eventTitle(event != null ? event.getTitle() : null)
                .reservationStartTime(reservation != null ? reservation.getStartTime() : null)
                .reservationEndTime(reservation != null ? reservation.getEndTime() : null)
                .roomLocationCode(roomLocationCode)
                .roomAddress(roomAddress)
                .build();
    }

    // start+ chức năng đặt phòng theo sự kiện (lịch sử thay đổi trạng thái người tham gia)
    private void recordHistory(
            EventParticipant participant,
            String action,
            EventParticipantInviteStatus fromInvite,
            EventParticipantInviteStatus toInvite,
            EventParticipantCheckInStatus fromCheckIn,
            EventParticipantCheckInStatus toCheckIn,
            String note,
            Authentication authentication
    ) {
        EventParticipantStatusHistory history = new EventParticipantStatusHistory();
        history.setId(UUID.randomUUID().toString());
        history.setParticipant(participant);
        history.setAction(action);
        history.setFromInviteStatus(fromInvite);
        history.setToInviteStatus(toInvite);
        history.setFromCheckInStatus(fromCheckIn);
        history.setToCheckInStatus(toCheckIn);
        history.setNote(note);
        history.setChangedByEmail(authentication != null ? authentication.getName() : null);
        history.setChangedAt(LocalDateTime.now());
        eventParticipantStatusHistoryRepository.save(history);
    }

    private EventParticipantStatusHistoryResponse toHistoryResponse(EventParticipantStatusHistory history) {
        return EventParticipantStatusHistoryResponse.builder()
                .id(history.getId())
                .action(history.getAction())
                .fromInviteStatus(history.getFromInviteStatus())
                .toInviteStatus(history.getToInviteStatus())
                .fromCheckInStatus(history.getFromCheckInStatus())
                .toCheckInStatus(history.getToCheckInStatus())
                .note(history.getNote())
                .changedByEmail(history.getChangedByEmail())
                .changedAt(history.getChangedAt())
                .build();
    }
    // end+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái người tham gia)
    @Override
    @Transactional(readOnly = true)
    public Page<AdminEventResponse> getAllEventsForAdmin(int page, int size, Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return eventRepository.findAll(pageRequest).map(event -> {
            Reservation res = event.getReservation();
            User user = res != null ? res.getUser() : null;

            String rCode = (res != null && res.getRoom() != null) ? res.getRoom().getLocationCode() : "N/A";
            
            String uName = "Unknown";
            String uEmail = "Unknown";
            
            if (user != null) {
                if (user.getUserInfo() != null) {
                    uName = user.getUserInfo().getFullName();
                    uEmail = user.getUserInfo().getEmail();
                }
                if (uName == null || uName.isBlank()) uName = user.getUsername();
                if (uEmail == null || uEmail.isBlank()) uEmail = user.getUsername();
            }

            return AdminEventResponse.builder()
                    .eventId(event.getId())
                    .reservationId(res != null ? res.getId() : null)
                    .title(event.getTitle())
                    .description(event.getDescription())
                    .visibility(event.getVisibility())
                    .startTime(res != null ? res.getStartTime() : null)
                    .endTime(res != null ? res.getEndTime() : null)
                    .status(res != null ? res.getStatus() : null)
                    .roomName(rCode)
                    .roomCode(rCode)
                    .userName(uName)
                    .userEmail(uEmail)
                    .createdAt(event.getCreatedAt())
                    .build();
        });
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

        assertOwnerOrAdminOrParticipant(event, authentication);

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
        assertOwnerOrAdminOrParticipant(event, authentication);

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

        boolean hasEmail = request.getEmail() != null && !request.getEmail().isBlank();
        if (!hasEmail) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "email is required");
        }

        final String emailValue = request.getEmail().trim();
        User user = userRepository.findByEmail(emailValue)
                .orElseThrow(() -> new CustomException(ResponseCode.USER_NOT_FOUND, "User not found"));
        if (eventParticipantRepository.findByEvent_IdAndUser_Id(event.getId(), user.getId()).isPresent()) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "User already invited");
        }

        EventParticipant participant = new EventParticipant();
        participant.setId(UUID.randomUUID().toString());
        participant.setEvent(event);
        participant.setUser(user);
        participant.setEmail(null);
        participant.setFullName(null);
        participant.setInviteStatus(EventParticipantInviteStatus.INVITED);
        participant.setCheckInStatus(EventParticipantCheckInStatus.NOT_CHECKED_IN);
        participant.setCreatedAt(LocalDateTime.now());
        participant.setUpdatedAt(LocalDateTime.now());

        eventParticipantRepository.save(participant);
        recordHistory(
                participant,
                "INVITED",
                null,
                participant.getInviteStatus(),
                null,
                participant.getCheckInStatus(),
                "Invited participant to event",
                authentication
        );

        // Gửi thông báo bằng hàm MỚI
        notificationService.noticeInviteParticipantToEvent(user.getId(), event.getTitle(), event.getReservation());

        return toParticipantResponse(participant);
    }

    @Override
    @Transactional
    public void removeParticipant(String participantId, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));
        assertOwnerOrAdmin(participant.getEvent().getReservation(), authentication);
        recordHistory(
                participant,
                "REMOVED",
                participant.getInviteStatus(),
                null,
                participant.getCheckInStatus(),
                null,
                "Removed participant from event",
                authentication
        );
        eventParticipantRepository.delete(participant);
    }

    // start+ chức năng đặt phòng theo sự kiện (người tham gia chấp nhận / từ chối)
    @Override
    @Transactional
    public EventParticipantResponse respondInvitation(String participantId, EventParticipantRespondRequest request, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));

        User currentUser = getCurrentUser(authentication);
        if (participant.getUser() == null || !participant.getUser().getId().equals(currentUser.getId())) {
            throw new CustomException(ResponseCode.PERMISSION_DENIED);
        }

        String resp = request.getResponse().trim().toUpperCase();
        EventParticipantInviteStatus nextStatus;
        if ("ACCEPT".equals(resp) || "ACCEPTED".equals(resp)) {
            nextStatus = EventParticipantInviteStatus.ACCEPTED;
        } else if ("DECLINE".equals(resp) || "DECLINED".equals(resp) || "REJECT".equals(resp) || "REJECTED".equals(resp)) {
            nextStatus = EventParticipantInviteStatus.DECLINED;
        } else {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "response must be ACCEPT or DECLINE");
        }

        EventParticipantInviteStatus from = participant.getInviteStatus();
        participant.setInviteStatus(nextStatus);
        participant.setUpdatedAt(LocalDateTime.now());
        eventParticipantRepository.save(participant);

        recordHistory(
                participant,
                "RESPONDED",
                from,
                nextStatus,
                participant.getCheckInStatus(),
                participant.getCheckInStatus(),
                "Participant responded to invitation",
                authentication
        );

        return toParticipantResponse(participant);
    }
    // end+ chức năng đặt phòng theo sự kiện (người tham gia chấp nhận / từ chối)

    // start+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái người tham gia)
    @Override
    @Transactional(readOnly = true)
    public List<EventParticipantStatusHistoryResponse> getParticipantHistory(String participantId, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));

        Event event = participant.getEvent();
        assertOwnerOrAdminOrParticipant(event, authentication);

        User currentUser = getCurrentUser(authentication);
        if (!isAdmin(authentication)) {
            Reservation reservation = event.getReservation();
            boolean isOwner = reservation.getUser() != null && reservation.getUser().getId().equals(currentUser.getId());
            boolean isSelf = participant.getUser() != null && participant.getUser().getId().equals(currentUser.getId());
            if (!isOwner && !isSelf) {
                throw new CustomException(ResponseCode.PERMISSION_DENIED);
            }
        }

        return eventParticipantStatusHistoryRepository.findByParticipant_IdOrderByChangedAtDesc(participantId).stream()
                .map(this::toHistoryResponse)
                .toList();
    }
    // end+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái người tham gia)

    // start+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)
    @Override
    @Transactional(readOnly = true)
    public List<EventParticipantResponse> getMyInvitations(Authentication authentication) {
        User currentUser = getCurrentUser(authentication);
        return eventParticipantRepository.findByUser_Id(currentUser.getId()).stream()
                .map(this::toParticipantResponse)
                .toList();
    }
    // end+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)
}
// end+ chức năng đặt phòng theo sự kiện (invite-only participants + check-in list)
