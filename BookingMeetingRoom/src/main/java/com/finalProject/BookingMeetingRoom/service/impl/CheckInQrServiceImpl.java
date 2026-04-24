package com.finalProject.BookingMeetingRoom.service.impl;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantCheckInStatus;
import com.finalProject.BookingMeetingRoom.common.enums.EventParticipantInviteStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.CheckInQrToken;
import com.finalProject.BookingMeetingRoom.model.entity.EventParticipant;
import com.finalProject.BookingMeetingRoom.model.entity.EventParticipantStatusHistory;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.request.ConsumeQrTokenRequest;
import com.finalProject.BookingMeetingRoom.model.response.CheckInQrTokenResponse;
import com.finalProject.BookingMeetingRoom.repository.CheckInQrTokenRepository;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantRepository;
import com.finalProject.BookingMeetingRoom.repository.EventParticipantStatusHistoryRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.service.CheckInQrService;
import com.finalProject.BookingMeetingRoom.service.ReservationService;
import com.finalProject.BookingMeetingRoom.service.RedisService;
import com.finalProject.BookingMeetingRoom.service.RealTimeService;

import lombok.RequiredArgsConstructor;
import java.util.concurrent.TimeUnit;

// start+ chức năng check-in bằng QR (generate + consume token)
@Service
@RequiredArgsConstructor
public class CheckInQrServiceImpl implements CheckInQrService {

    private static final int DEFAULT_EXPIRE_MINUTES = 15;
    private static final int OTP_LENGTH = 6;
    private static final Random OTP_RANDOM = new Random();

    private final CheckInQrTokenRepository checkInQrTokenRepository;
    private final ReservationRepository reservationRepository;
    private final EventParticipantRepository eventParticipantRepository;
    private final EventParticipantStatusHistoryRepository eventParticipantStatusHistoryRepository;
    private final ReservationService reservationService;
    private final RedisService redisService;
    private final RealTimeService realTimeService;

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

    // start+ chức năng đặt phòng theo sự kiện (OTP check-in)
    private String generateOtpValue() {
        int value = OTP_RANDOM.nextInt((int) Math.pow(10, OTP_LENGTH));
        return String.format("%0" + OTP_LENGTH + "d", value);
    }

    private void recordParticipantHistory(
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
    // end+ chức năng đặt phòng theo sự kiện (OTP check-in)

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

    // start+ chức năng đặt phòng theo sự kiện (OTP check-in cho người tham gia)
    @Override
    @Transactional
    public CheckInQrTokenResponse generateParticipantOtp(String participantId, Authentication authentication) {
        EventParticipant participant = eventParticipantRepository.findById(participantId)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Participant not found"));

        Reservation reservation = participant.getEvent().getReservation();
        assertOwnerOrAdmin(reservation, authentication);

        if (participant.getInviteStatus() != EventParticipantInviteStatus.ACCEPTED) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Participant must accept invitation before check-in");
        }
        if (participant.getCheckInStatus() == EventParticipantCheckInStatus.CHECKED_IN) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Participant already checked in");
        }

        String otp;
        int attempts = 0;
        do {
            otp = generateOtpValue();
            attempts++;
        } while (checkInQrTokenRepository.existsById(otp) && attempts < 20);
        if (checkInQrTokenRepository.existsById(otp)) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Unable to generate OTP");
        }

        CheckInQrToken token = new CheckInQrToken();
        token.setToken(otp);
        token.setReservation(reservation);
        token.setEventParticipant(participant);
        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusMinutes(DEFAULT_EXPIRE_MINUTES));
        token.setUsedAt(null);
        checkInQrTokenRepository.save(token);

        recordParticipantHistory(
                participant,
                "OTP_CREATED",
                participant.getInviteStatus(),
                participant.getInviteStatus(),
                participant.getCheckInStatus(),
                participant.getCheckInStatus(),
                "OTP created for participant check-in",
                authentication
        );

        return toResponse(token);
    }
    // end+ chức năng đặt phòng theo sự kiện (OTP check-in cho người tham gia)

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
                String participantEmail = participant.getUser().getUserInfo() != null
                        ? participant.getUser().getUserInfo().getEmail()
                        : participant.getUser().getUsername();
                if (participantEmail == null || !participantEmail.equalsIgnoreCase(authentication.getName())) {
                    throw new CustomException(ResponseCode.PERMISSION_DENIED);
                }
            }

            if (participant.getInviteStatus() != EventParticipantInviteStatus.ACCEPTED) {
                throw new CustomException(ResponseCode.VALIDATION_FAILED, "Invitation not accepted");
            }

            EventParticipantCheckInStatus fromStatus = participant.getCheckInStatus();
            participant.setCheckInStatus(EventParticipantCheckInStatus.CHECKED_IN);
            participant.setCheckInTime(LocalDateTime.now());
            participant.setUpdatedAt(LocalDateTime.now());
            eventParticipantRepository.save(participant);

            recordParticipantHistory(
                    participant,
                    "CHECKED_IN",
                    participant.getInviteStatus(),
                    participant.getInviteStatus(),
                    fromStatus,
                    participant.getCheckInStatus(),
                    "Participant checked in via token",
                    authentication
            );
        } else {
            Reservation reservation = token.getReservation();
            assertOwnerOrAdmin(reservation, authentication);
            reservationService.checkIn(reservation.getId(), authentication);
        }

        token.setUsedAt(LocalDateTime.now());
        checkInQrTokenRepository.save(token);
    }

    // start+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)
    @Override
    @Transactional
    public CheckInQrTokenResponse getLiveEventCode(String reservationId, Authentication authentication) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));
        assertOwnerOrAdmin(reservation, authentication);

        // start+ Chỉ hiển thị code sau khi chủ phòng đã check-in (IN_USE)
        if (reservation.getStatus() != ReservationStatus.IN_USE) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Owner must check-in first to get the event code");
        }
        // end+ Chỉ hiển thị code sau khi chủ phòng đã check-in (IN_USE)

        String redisKey = "EVENT_CODE:" + reservationId;
        String code = (String) redisService.getValue(redisKey);

        if (code == null) {
            code = generateOtpValue();
            redisService.setValue(redisKey, code, 1, TimeUnit.MINUTES);
        }

        return CheckInQrTokenResponse.builder()
                .token(code)
                .expiresAt(LocalDateTime.now().plusSeconds(60))
                .build();
    }

    @Override
    @Transactional
    public void checkInWithCode(String reservationId, String code, Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new CustomException(ResponseCode.ACCESS_DENIED);
        }

        String redisKey = "EVENT_CODE:" + reservationId;
        String validCode = (String) redisService.getValue(redisKey);

        if (validCode == null || !validCode.equals(code)) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Invalid or expired check-in code");
        }

        EventParticipant participant = eventParticipantRepository.findByReservationIdAndEmail(reservationId, authentication.getName())
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "You are not a participant of this event"));

        if (participant.getInviteStatus() != EventParticipantInviteStatus.ACCEPTED) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Invitation not accepted");
        }

        if (participant.getCheckInStatus() == EventParticipantCheckInStatus.CHECKED_IN) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Already checked in");
        }

        EventParticipantCheckInStatus fromStatus = participant.getCheckInStatus();
        participant.setCheckInStatus(EventParticipantCheckInStatus.CHECKED_IN);
        participant.setCheckInTime(LocalDateTime.now());
        participant.setUpdatedAt(LocalDateTime.now());
        eventParticipantRepository.save(participant);

        recordParticipantHistory(
                participant,
                "CHECKED_IN_CODE",
                participant.getInviteStatus(),
                participant.getInviteStatus(),
                fromStatus,
                participant.getCheckInStatus(),
                "Participant checked in via 6-digit code",
                authentication
        );

        realTimeService.sendParticipantUpdate(reservationId);
    }
    // end+ chức năng đặt phòng theo sự kiện (mã code 6 số thay đổi mỗi phút)
}
// end+ chức năng check-in bằng QR (generate + consume token)
