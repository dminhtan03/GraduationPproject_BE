package com.finalProject.BookingMeetingRoom.service.room;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RoomServiceTest {
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private SeatRepository seatRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @InjectMocks
    private SeatServiceImpl seatService;

    @BeforeEach
    void setUp() {
        floorRepository = mock(FloorRepository.class);
        seatRepository = mock(SeatRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        seatService = new SeatServiceImpl(floorRepository, seatRepository, reservationRepository);
    }

    @Test
    void testSearchSeats_returnsAvailableSeats() {
        var floor = new Floor();
        var seat = new Seat();
        seat.setId("seat1");
        seat.setLocationCode("A1");
        seat.setScore(10.0);

        var request = new SeatSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));

        when(floorRepository.findById("1L")).thenReturn(Optional.of(floor));
        when(seatRepository.findByFloor(floor)).thenReturn(List.of(seat));
        when(reservationRepository.findOverlappingReservations(eq("seat1"), any(), any()))
                .thenReturn(Collections.emptyList());

        var result = seatService.searchSeats(request);

        assertEquals(1, result.size());
        assertEquals(SeatStatus.AVAILABLE, result.get(0).getStatus());
        verify(floorRepository, times(1)).findById("1L");
    }

    @Test
    void testSearchSeats_whenSeatReserved_thenFilteredOut() {
        var floor = new Floor();
        var seat = new Seat();
        seat.setId("seat1");

        var request = new SeatSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));

        when(floorRepository.findById("1L")).thenReturn(Optional.of(floor));
        when(seatRepository.findByFloor(floor)).thenReturn(List.of(seat));

        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.RESERVED);

        when(reservationRepository.findOverlappingReservations(eq("seat1"), any(), any()))
                .thenReturn(List.of(reservation));

        var result = seatService.searchSeats(request);

        assertEquals(0, result.size());
    }

    @Test
    void testSearchSeats_floorNotFound_throwsCustomException() {
        SeatSearchRequest request = new SeatSearchRequest();
        request.setFloorId("999L");

        when(floorRepository.findById("999L")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> seatService.searchSeats(request));

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    void testSearchSeats_unexpectedError_throwsInternalServerError() {
        SeatSearchRequest request = new SeatSearchRequest();
        request.setFloorId("1L");

        when(floorRepository.findById("1L")).thenThrow(new RuntimeException("DB failure"));

        CustomException exception = assertThrows(CustomException.class,
                () -> seatService.searchSeats(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void testGetSeatDetails_success() {
        SeatResponseProjection mockProjection = mock(SeatResponseProjection.class);

        when(mockProjection.getUserId()).thenReturn("u1");
        when(mockProjection.getUserName()).thenReturn("John Doe");
        when(mockProjection.getCheckInTime()).thenReturn(LocalDateTime.of(2025, 7, 10, 14, 0));

        when(seatRepository.findSeatInMap("seat123")).thenReturn(mockProjection);

        var result = seatService.getSeatDetails("seat123");

        assertEquals("u1", result.getUserId());
        assertEquals("John Doe", result.getUserName());
        assertEquals(LocalDateTime.of(2025, 7, 10, 14, 0), result.getCheckInTime());
    }

    @Test
    void testGetSeatDetails_seatNotFound() {
        when(seatRepository.findSeatInMap("invalid")).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> seatService.getSeatDetails("invalid"));

        assertEquals(ResponseCode.SEAT_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void testGetSeatDetails_unexpectedError() {
        when(seatRepository.findSeatInMap("seatX")).thenThrow(new RuntimeException());

        CustomException ex = assertThrows(CustomException.class,
                () -> seatService.getSeatDetails("seatX"));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void testSearchSeats_reservationNotOverlapping() {
        var floor = new Floor();
        var seat = new Seat();
        seat.setId("seat2");
        seat.setLocationCode("A2");
        seat.setScore(20.0);

        var request = new SeatSearchRequest();
        request.setFloorId("2L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 10, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 11, 0));

        // reservation trước giờ tìm
        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setStartTime(LocalDateTime.of(2025, 7, 10, 8, 0));
        reservation.setEndTime(LocalDateTime.of(2025, 7, 10, 9, 0));

        when(floorRepository.findById("2L")).thenReturn(Optional.of(floor));
        when(seatRepository.findByFloor(floor)).thenReturn(List.of(seat));
        when(reservationRepository.findOverlappingReservations(eq("seat2"), any(), any()))
                .thenReturn(Collections.emptyList()); // giả định logic overlap được filter ở query

        var result = seatService.searchSeats(request);

        assertEquals(1, result.size());
        assertEquals("seat2", result.get(0).getSeatId());
    }

    @Test
    void testSearchSeats_floorExistsButNoSeats() {
        var floor = new Floor();

        var request = new SeatSearchRequest();
        request.setFloorId("5L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 9, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 10, 0));

        when(floorRepository.findById("5L")).thenReturn(Optional.of(floor));
        when(seatRepository.findByFloor(floor)).thenReturn(Collections.emptyList());

        var result = seatService.searchSeats(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchSeats_startTimeAfterEndTime_shouldThrowException() {
        SeatSearchRequest request = new SeatSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 12, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 10, 0));  // startTime > endTime

        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));

        CustomException ex = assertThrows(CustomException.class,
                () -> seatService.searchSeats(request));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void testSearchSeats_startTimeNull_shouldThrowException() {
        SeatSearchRequest request = new SeatSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(null);
        request.setEndTime(LocalDateTime.now().plusHours(1));
        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));
        CustomException exception = assertThrows(CustomException.class,
                () -> seatService.searchSeats(request));
        assertEquals(ResponseCode.VALIDATION_FAILED, exception.getResponseCode());
    }

    @Test
    void testSearchSeats_endTimeNull_shouldThrowException() {
        SeatSearchRequest request = new SeatSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(null);
        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));
        CustomException exception = assertThrows(CustomException.class,
                () -> seatService.searchSeats(request));
        assertEquals(ResponseCode.VALIDATION_FAILED, exception.getResponseCode());
    }
}
