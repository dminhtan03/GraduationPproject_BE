package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.CheckInQrToken;
import com.finalProject.BookingMeetingRoom.model.entity.EventParticipant;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.request.ConsumeQrTokenRequest;
import com.finalProject.BookingMeetingRoom.model.response.CheckInQrTokenResponse;
import com.finalProject.BookingMeetingRoom.repository.CheckInQrTokenRepository;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.service.CheckInQrService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;

import lombok.RequiredArgsConstructor;

// start+ chức năng check-in bằng QR (generate + consume token)
@Service
@RequiredArgsConstructor
public class CheckInQrServiceImpl implements CheckInQrService {

    private static final int DEFAULT_EXPIRE_MINUTES = 15;

    private final CheckInQrTokenRepository checkInQrTokenRepository;
    private final ReservationRepository reservationRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final ReservationService reservationService;

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

    private CheckInQrTokenResponse toResponse(CheckInQrToken token) {
        return CheckInQrTokenResponse.builder()
                .token(token.getToken())
                .expiresAt(token.getExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public CheckInQrTokenResponse generateReservationQr(String reservationId, Authentication authentication) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));
        assertOwnerOrAdmin(reservation, authentication);

        CheckInQrToken token = new CheckInQrToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setReservation(reservation);
        token.setEventParticipant(null);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES));
        token.setUsedAt(null);
        checkInQrTokenRepository.save(token);

        return toResponse(token);
    }

    @Override
    @Transactional
    public CheckInQrTokenResponse generateParticipantQr(String participantId, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));

        Reservation reservation = participant.getEvent().getReservation();
        assertOwnerOrAdmin(reservation, authentication);

        CheckInQrToken token = new CheckInQrToken();
        token.setToken(UUID.randomUUID().toString().replace("-", ""));
        token.setReservation(reservation);
        token.setEventParticipant(participant);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES));
        token.setUsedAt(null);
        checkInQrTokenRepository.save(token);

        return toResponse(token);
    }

    @Override
    @Transactional
    public void consumeQr(ConsumeQrTokenRequest request, Authentication authentication) {
        CheckInQrToken token = checkInQrTokenRepository.findById(request.getToken())
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Token not found"));

        if (token.getUsedAt() != null) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Token already used");
        }
        if (token.getExpiresAt() != null && LocalDateTime.now().isAfter(token.getExpiresAt())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Token expired");
        }

        if (token.getEventParticipant() != null) {
            EventParticipant participant = token.getEventParticipant();
            if (participant.getUser() != null && !isAdmin(authentication)) {
                if (authentication == null || authentication.getName() == null) {
                    throw new CustomException(ResponseCode.ACCESS_DENIED);
                }
                if (!participant.getUser().getUsername().equalsIgnoreCase(authentication.getName())) {
                    throw new CustomException(ResponseCode.PERMISSION_DENIED);
                }
            }

            participant.setCheckInStatus(EventParticipantCheckInStatus.CHECKED_IN);
            participant.setCheckInTime(LocalDateTime.now());
            participant.setUpdatedAt(LocalDateTime.now());
            eventParticipantRepository.save(participant);
        } else {
            Reservation reservation = token.getReservation();
            assertOwnerOrAdmin(reservation, authentication);
            reservationService.checkIn(reservation.getId(), authentication);
        }

        token.setUsedAt(LocalDateTime.now());
        checkInQrTokenRepository.save(token);
    }
}
// end+ chức năng check-in bằng QR (generate + consume token)
