package com.finalProject.BookingMeetingRoom.service.room;

import com.cloudinary.Cloudinary;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.repository.AmenityRepository;
import com.finalProject.BookingMeetingRoom.repository.FeedbackRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorDecorationRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private FloorRepository floorRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private AmenityRepository amenityRepository;
    @Mock
    private RoomImageRepository roomImageRepository;
    @Mock
    private Cloudinary cloudinary;
    @Mock
    private AcademicScheduleService academicScheduleService;
    @Mock
    private FloorDecorationRepository floorDecorationRepository;

    @InjectMocks
    private RoomServiceImpl service;

    @Test
    void searchRooms_shouldThrowFloorNotFound_whenFloorMissing() {
        when(floorRepository.findById("f-missing")).thenReturn(Optional.empty());

        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("f-missing");
        request.setStartTime(LocalDateTime.now());
        request.setEndTime(LocalDateTime.now().plusHours(1));

        CustomException ex = assertThrows(CustomException.class, () -> service.searchRooms(request));

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void searchRooms_shouldThrowValidationFailed_whenStartOrEndMissing() {
        Floor floor = new Floor();
        floor.setId("f-1");
        when(floorRepository.findById("f-1")).thenReturn(Optional.of(floor));

        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("f-1");
        request.setStartTime(null);
        request.setEndTime(LocalDateTime.now().plusHours(1));

        CustomException ex = assertThrows(CustomException.class, () -> service.searchRooms(request));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void searchRooms_shouldThrowValidationFailed_whenStartNotBeforeEnd() {
        Floor floor = new Floor();
        floor.setId("f-1");
        when(floorRepository.findById("f-1")).thenReturn(Optional.of(floor));

        LocalDateTime now = LocalDateTime.now();

        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("f-1");
        request.setStartTime(now.plusHours(1));
        request.setEndTime(now);

        CustomException ex = assertThrows(CustomException.class, () -> service.searchRooms(request));

        assertEquals(ResponseCode.VALIDATION_FAILED, ex.getResponseCode());
    }

    @Test
    void searchRooms_shouldReturnAvailableRooms_whenNoConflictAndNoLearning() {
        Floor floor = new Floor();
        floor.setId("f-1");

        Room room = new Room();
        room.setId("r-1");
        room.setLocationCode("A101");
        room.setStatus(RoomStatus.AVAILABLE);

        LocalDateTime start = LocalDateTime.of(2026, 4, 24, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 24, 10, 0);

        when(floorRepository.findById("f-1")).thenReturn(Optional.of(floor));
        when(roomRepository.findByFloor(floor)).thenReturn(List.of(room));
        when(reservationRepository.findOverlappingReservations("r-1", start, end)).thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning("r-1", start, end)).thenReturn(false);

        RoomSearchRequest request = new RoomSearchRequest();
        request.setFloorId("f-1");
        request.setStartTime(start);
        request.setEndTime(end);

        List<RoomSearchResponse> result = service.searchRooms(request);

        assertEquals(1, result.size());
        assertEquals("r-1", result.get(0).getRoomId());
        assertEquals(RoomStatus.AVAILABLE, result.get(0).getStatus());
        assertTrue(result.get(0).getScore() >= 0);
    }
}
