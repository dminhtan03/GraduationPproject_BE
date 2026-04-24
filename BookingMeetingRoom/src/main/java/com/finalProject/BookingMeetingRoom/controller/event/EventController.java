package com.finalProject.BookingMeetingRoom.controller.event;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.EventCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.EventParticipantRespondRequest;
import com.finalProject.BookingMeetingRoom.service.EventService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// start+ chức năng đặt phòng theo sự kiện (event + participants APIs)
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<?> getAllEventsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.getAllEventsForAdmin(page, size, authentication)));
    }

    @PostMapping
    public ResponseEntity<?> createEvent(@Valid @RequestBody EventCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.createEvent(request, authentication)));
    }

    @GetMapping("/by-reservation/{reservationId}")
    public ResponseEntity<?> getEventByReservation(@PathVariable String reservationId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.getEventByReservationId(reservationId, authentication)));
    }

    @GetMapping("/{eventId}/participants")
    public ResponseEntity<?> getParticipants(@PathVariable String eventId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.getParticipants(eventId, authentication)));
    }

    @PostMapping("/participants")
    public ResponseEntity<?> inviteParticipant(@Valid @RequestBody EventParticipantCreateRequest request, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.inviteParticipant(request, authentication)));
    }

    // start+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)
    @GetMapping("/my-invitations")
    public ResponseEntity<?> getMyInvitations(Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.getMyInvitations(authentication)));
    }
    // end+ chức năng đặt phòng theo sự kiện (danh sách lời mời của người tham gia)

    // start+ chức năng đặt phòng theo sự kiện (participant chấp nhận / từ chối)
    @PutMapping("/participants/{participantId}/respond")
    public ResponseEntity<?> respondInvitation(
            @PathVariable String participantId,
            @Valid @RequestBody EventParticipantRespondRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.respondInvitation(participantId, request, authentication)));
    }
    // end+ chức năng đặt phòng theo sự kiện (participant chấp nhận / từ chối)

    // start+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái)
    @GetMapping("/participants/{participantId}/history")
    public ResponseEntity<?> getParticipantHistory(@PathVariable String participantId, Authentication authentication) {
        return ResponseEntity.ok(Response.ofSucceeded(eventService.getParticipantHistory(participantId, authentication)));
    }
    // end+ chức năng đặt phòng theo sự kiện (popup lịch sử thay đổi trạng thái)

    @DeleteMapping("/participants/{participantId}")
    public ResponseEntity<?> removeParticipant(@PathVariable String participantId, Authentication authentication) {
        eventService.removeParticipant(participantId, authentication);
        return ResponseEntity.ok(Response.ofSucceeded("Participant removed successfully"));
    }
}
// end+ chức năng đặt phòng theo sự kiện (event + participants APIs)
