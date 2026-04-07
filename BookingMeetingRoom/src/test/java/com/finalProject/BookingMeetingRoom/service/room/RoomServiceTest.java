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
    private RoomRepository roomRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @InjectMocks
    private RoomServiceImpl roomService;

    @BeforeEach
    void setUp() {
        floorRepository = mock(FloorRepository.class);
        roomRepository = mock(RoomRepository.class);
        reservationRepository = mock(ReservationRepository.class);
        roomService = new RoomServiceImpl(floorRepository, roomRepository, reservationRepository);
    }

    @Test
    void testSearchRooms_returnsAvailableRooms() {
        var floor = new Floor();
        var room = new Room();
        room.setId("room1");
        room.setLocationCode("A1");
        room.setScore(10.0);

        var request = new RoomSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));

        when(floorRepository.findById("1L")).thenReturn(Optional.of(floor));
        when(roomRepository.findByFloor(floor)).thenReturn(List.of(room));
        when(reservationRepository.findOverlappingReservations(eq("room1"), any(), any()))
                .thenReturn(Collections.emptyList());

        var result = roomService.searchRooms(request);

        assertEquals(1, result.size());
        assertEquals(RoomStatus.AVAILABLE, result.get(0).getStatus());
        verify(floorRepository, times(1)).findById("1L");
    }

    @Test
    void testSearchRooms_whenRoomReserved_thenFilteredOut() {
        var floor = new Floor();
        var room = new Room();
        room.setId("room1");

        var request = new RoomSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));

        when(floorRepository.findById("1L")).thenReturn(Optional.of(floor));
        when(roomRepository.findByFloor(floor)).thenReturn(List.of(room));

        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.RESERVED);

        when(reservationRepository.findOverlappingReservations(eq("room1"), any(), any()))
                .thenReturn(List.of(reservation));

        var result = roomService.searchRooms(request);

        assertEquals(0, result.size());
    }

    @Test
    void testSearchRooms_floorNotFound_throwsCustomException() {
        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("999L");

        when(floorRepository.findById("999L")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class,
                () -> roomService.searchRooms(request));

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());
    }

    @Test
    void testSearchRooms_unexpectedError_throwsInternalServerError() {
        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("1L");

        when(floorRepository.findById("1L")).thenThrow(new RuntimeException("DB failure"));

        CustomException exception = assertThrows(CustomException.class,
                () -> roomService.searchRooms(request));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
    }

    @Test
    void testGetRoomDetails_success() {
        RoomResponseProjection mockProjection = mock(RoomResponseProjection.class);

        when(mockProjection.getUserId()).thenReturn("u1");
        when(mockProjection.getUserName()).thenReturn("John Doe");
        when(mockProjection.getCheckInTime()).thenReturn(LocalDateTime.of(2025, 7, 10, 14, 0));

        when(roomRepository.findRoomInMap("room123")).thenReturn(mockProjection);

        var result = roomService.getRoomDetails("room123");

        assertEquals("u1", result.getUserId());
        assertEquals("John Doe", result.getUserName());
        assertEquals(LocalDateTime.of(2025, 7, 10, 14, 0), result.getCheckInTime());
    }

    @Test
    void testGetRoomDetails_roomNotFound() {
        when(roomRepository.findRoomInMap("invalid")).thenReturn(null);

        CustomException ex = assertThrows(CustomException.class,
                () -> roomService.getRoomDetails("invalid"));

        assertEquals(ResponseCode.ROOM_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void testGetRoomDetails_unexpectedError() {
        when(roomRepository.findRoomInMap("roomX")).thenThrow(new RuntimeException());

        CustomException ex = assertThrows(CustomException.class,
                () -> roomService.getRoomDetails("roomX"));

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void testSearchRooms_reservationNotOverlapping() {
        var floor = new Floor();
        var room = new Room();
        room.setId("room2");
        room.setLocationCode("A2");
        room.setScore(20.0);

        var request = new RoomSearchRequest();
        request.setFloorId("2L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 10, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 11, 0));

        // reservation trước giờ tìm
        Reservation reservation = new Reservation();
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setStartTime(LocalDateTime.of(2025, 7, 10, 8, 0));
        reservation.setEndTime(LocalDateTime.of(2025, 7, 10, 9, 0));

        when(floorRepository.findById("2L")).thenReturn(Optional.of(floor));
        when(roomRepository.findByFloor(floor)).thenReturn(List.of(room));
        when(reservationRepository.findOverlappingReservations(eq("room2"), any(), any()))
                .thenReturn(Collections.emptyList()); // giả định logic overlap được filter ở query

        var result = roomService.searchRooms(request);

        assertEquals(1, result.size());
        assertEquals("room2", result.get(0).getRoomId());
    }

    @Test
    void testSearchRooms_floorExistsButNoRooms() {
        var floor = new Floor();

        var request = new RoomSearchRequest();
        request.setFloorId("5L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 9, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 10, 0));

        when(floorRepository.findById("5L")).thenReturn(Optional.of(floor));
        when(roomRepository.findByFloor(floor)).thenReturn(Collections.emptyList());

        var result = roomService.searchRooms(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSearchRooms_startTimeAfterEndTime_shouldThrowException() {
        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.of(2025, 7, 10, 12, 0));
        request.setEndTime(LocalDateTime.of(2025, 7, 10, 10, 0));  // startTime > endTime

        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));

        CustomException ex = assertThrows(CustomException.class,
                () -> roomService.searchRooms(request));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void testSearchRooms_startTimeNull_shouldThrowException() {
        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(null);
        request.setEndTime(LocalDateTime.now().plusHours(1));
        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));
        CustomException exception = assertThrows(CustomException.class,
                () -> roomService.searchRooms(request));
        assertEquals(ResponseCode.VALIDATION_FAILED, exception.getResponseCode());
    }

    @Test
    void testSearchRooms_endTimeNull_shouldThrowException() {
        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("1L");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(null);
        when(floorRepository.findById("1L")).thenReturn(Optional.of(new Floor()));
        CustomException exception = assertThrows(CustomException.class,
                () -> roomService.searchRooms(request));
        assertEquals(ResponseCode.VALIDATION_FAILED, exception.getResponseCode());
    }
}
