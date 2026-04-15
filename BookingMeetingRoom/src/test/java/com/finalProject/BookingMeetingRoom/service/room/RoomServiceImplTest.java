package com.finalProject.BookingMeetingRoom.service.room;

import com.cloudinary.Cloudinary;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.AmenityRepository;
import com.finalProject.BookingMeetingRoom.repository.FeedbackRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorDecorationRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RoomServiceImplTest {

    @Test
    void addRoomShouldThrowFloorNotFound_whenFloorMissing() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        RoomCreateRequest request = RoomCreateRequest.builder()
                .floorId("F404")
                .locationCode("AL-101")
                .status(RoomStatus.AVAILABLE)
                .capacity(10)
                .build();

        when(floorRepository.findById("F404")).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.addRoom(request, null));
        assertEquals(ResponseCode.FLOOR_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void addRoomShouldThrowRoomAlreadyExists_whenLocationCodeExists() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Floor floor = new Floor();
        floor.setId("F1");

        RoomCreateRequest request = RoomCreateRequest.builder()
                .floorId("F1")
                .locationCode("AL-101")
                .status(RoomStatus.AVAILABLE)
                .capacity(10)
                .build();

        when(floorRepository.findById("F1")).thenReturn(Optional.of(floor));
        when(roomRepository.existsByLocationCode("AL-101")).thenReturn(true);

        CustomException ex = assertThrows(CustomException.class, () -> service.addRoom(request, null));
        assertEquals(ResponseCode.ROOM_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void addRoomShouldSaveRoomAndImageFromUrl_whenImageUrlProvided() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Floor floor = new Floor();
        floor.setId("F1");

        RoomCreateRequest request = RoomCreateRequest.builder()
                .floorId("F1")
                .locationCode("AL-101")
                .status(RoomStatus.AVAILABLE)
                .capacity(0)
                .score(null)
                .imageUrl("https://example.com/img.png")
                .publicId("pid")
                .build();

        when(floorRepository.findById("F1")).thenReturn(Optional.of(floor));
        when(roomRepository.existsByLocationCode("AL-101")).thenReturn(false);

        service.addRoom(request, null);

        ArgumentCaptor<Room> roomCap = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCap.capture());
        assertEquals("AL-101", roomCap.getValue().getLocationCode());
        assertEquals(0.0, roomCap.getValue().getScore());
        assertSame(floor, roomCap.getValue().getFloor());

        verify(roomImageRepository).save(any());
    }

    @Test
    void addRoomShouldSaveRoomWithoutImage_whenNoImageProvided() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Floor floor = new Floor();
        floor.setId("F1");

        RoomCreateRequest request = RoomCreateRequest.builder()
                .floorId("F1")
                .locationCode("B-01_🚀")
                .status(RoomStatus.AVAILABLE)
                .capacity(999)
                .score(3.0)
                .build();

        when(floorRepository.findById("F1")).thenReturn(Optional.of(floor));
        when(roomRepository.existsByLocationCode("B-01_🚀")).thenReturn(false);

        service.addRoom(request, null);

        verify(roomRepository).save(any(Room.class));
        verify(roomImageRepository, never()).save(any());
    }

    @Test
    void addRoomShouldSaveAmenities_whenAmenityIdsProvided() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Floor floor = new Floor();
        floor.setId("F1");

        Amenity a1 = new Amenity();
        a1.setId("A1");
        Amenity a2 = new Amenity();
        a2.setId("A2");

        RoomCreateRequest request = RoomCreateRequest.builder()
                .floorId("F1")
                .locationCode("PHÒNG-ĐẶC-BIỆT-01")
                .status(RoomStatus.AVAILABLE)
                .capacity(10)
                .amenityIds(List.of("A1", "A2"))
                .build();

        when(floorRepository.findById("F1")).thenReturn(Optional.of(floor));
        when(roomRepository.existsByLocationCode("PHÒNG-ĐẶC-BIỆT-01")).thenReturn(false);
        when(amenityRepository.findAllById(List.of("A1", "A2"))).thenReturn(List.of(a1, a2));

        service.addRoom(request, null);

        ArgumentCaptor<Room> roomCap = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCap.capture());
        assertNotNull(roomCap.getValue().getAmenities());
        assertEquals(2, roomCap.getValue().getAmenities().size());
    }

    @Test
    void updateRoomShouldUpdateFieldsAndSave() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        room.setCapacity(5);
        room.setStatus(RoomStatus.AVAILABLE);

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        when(amenityRepository.findAllById(any())).thenReturn(List.of());

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .roomId("R1")
                .capacity(20)
                .status(RoomStatus.UNAVAILABLE)
                .amenityIds(List.of("A1", "A2"))
                .build();

        service.updateRoom(request);

        ArgumentCaptor<Room> roomCap = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCap.capture());
        assertEquals(20, roomCap.getValue().getCapacity());
        assertEquals(RoomStatus.UNAVAILABLE, roomCap.getValue().getStatus());
    }

    @Test
    void updateRoomShouldThrowRoomNotFound_whenMissing() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        when(roomRepository.findById("R404")).thenReturn(Optional.empty());

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .roomId("R404")
                .capacity(20)
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> service.updateRoom(request));
        assertEquals(ResponseCode.ROOM_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void updateRoomShouldNotChangeAmenities_whenAmenityIdsNull() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Amenity existing = new Amenity();
        existing.setId("A1");

        Room room = new Room();
        room.setId("R1");
        room.setAmenities(List.of(existing));

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .roomId("R1")
                .capacity(20)
                .amenityIds(null)
                .build();

        service.updateRoom(request);

        ArgumentCaptor<Room> roomCap = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCap.capture());
        assertEquals(1, roomCap.getValue().getAmenities().size());
        assertEquals("A1", roomCap.getValue().getAmenities().get(0).getId());
        verify(amenityRepository, never()).findAllById(any());
    }

    @Test
    void updateRoomShouldReplaceAmenities_whenAmenityIdsProvided() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));

        Amenity a2 = new Amenity();
        a2.setId("A2");
        when(amenityRepository.findAllById(List.of("A2"))).thenReturn(List.of(a2));

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .roomId("R1")
                .amenityIds(List.of("A2"))
                .build();

        service.updateRoom(request);

        ArgumentCaptor<Room> roomCap = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(roomCap.capture());
        assertEquals(1, roomCap.getValue().getAmenities().size());
        assertEquals("A2", roomCap.getValue().getAmenities().get(0).getId());
    }

    @Test
    void updateRoomShouldThrowInternalServerError_whenSaveFails() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        doThrow(new RuntimeException("db")).when(roomRepository).save(any(Room.class));

        RoomUpdateRequest request = RoomUpdateRequest.builder()
                .roomId("R1")
                .capacity(20)
                .build();

        CustomException ex = assertThrows(CustomException.class, () -> service.updateRoom(request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void getRoomDetailShouldReturnLearningStatus_whenAcademicScheduleBusy() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        room.setLocationCode("AL-101");
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(10);
        room.setScore(4.5);
        room.setImages(null);

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        when(roomRepository.findCurrentUserByRoomId("R1")).thenReturn(null);
        when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("R1")).thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning(eq("R1"), any(), any())).thenReturn(true);

        var res = service.getRoomDetail("R1");
        assertNotNull(res);
        assertEquals(RoomStatus.LEARNING, res.getStatus());
        assertEquals("AL-101", res.getLocationCode());
    }

    @Test
    void getRoomDetailShouldReturnAvailableStatus_whenNotBusy() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        room.setLocationCode("AL-101");
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(10);
        room.setScore(4.5);
        room.setImages(null);

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        when(roomRepository.findCurrentUserByRoomId("R1")).thenReturn(null);
        when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("R1")).thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning(eq("R1"), any(), any())).thenReturn(false);

        var res = service.getRoomDetail("R1");
        assertNotNull(res);
        assertEquals(RoomStatus.AVAILABLE, res.getStatus());
    }

    @Test
    void getRoomDetailShouldReturnEmptyImagesList_whenImagesNull() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        room.setLocationCode("AL-101");
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(10);
        room.setScore(4.5);
        room.setImages(null);

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        when(roomRepository.findCurrentUserByRoomId("R1")).thenReturn(null);
        when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("R1")).thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning(eq("R1"), any(), any())).thenReturn(false);

        var res = service.getRoomDetail("R1");
        assertNotNull(res);
        assertNotNull(res.getImages());
        assertTrue(res.getImages().isEmpty());
    }

    @Test
    void getRoomDetailShouldReturnCurrentUserFields_whenProjectionPresent() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        Room room = new Room();
        room.setId("R1");
        room.setLocationCode("AL-101");
        room.setStatus(RoomStatus.AVAILABLE);
        room.setCapacity(10);
        room.setScore(4.5);
        room.setImages(null);

        RoomRepository.CurrentUserProjection projection = mock(RoomRepository.CurrentUserProjection.class);
        when(projection.getUserId()).thenReturn("U1");
        when(projection.getUserName()).thenReturn("Name");

        when(roomRepository.findById("R1")).thenReturn(Optional.of(room));
        when(roomRepository.findCurrentUserByRoomId("R1")).thenReturn(projection);
        when(feedbackRepository.findByRoomIdOrderByCreatedAtDesc("R1")).thenReturn(List.of());
        when(academicScheduleService.isRoomBusyWithLearning(eq("R1"), any(), any())).thenReturn(false);

        var res = service.getRoomDetail("R1");
        assertEquals("U1", res.getCurrentUserId());
        assertEquals("Name", res.getCurrentUserName());
    }

    @Test
    void getRoomDetailShouldThrowRoomNotFound_whenMissing() {
        FloorRepository floorRepository = mock(FloorRepository.class);
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        FeedbackRepository feedbackRepository = mock(FeedbackRepository.class);
        AmenityRepository amenityRepository = mock(AmenityRepository.class);
        RoomImageRepository roomImageRepository = mock(RoomImageRepository.class);
        Cloudinary cloudinary = mock(Cloudinary.class);
        AcademicScheduleService academicScheduleService = mock(AcademicScheduleService.class);
        FloorDecorationRepository floorDecorationRepository = mock(FloorDecorationRepository.class);

        RoomServiceImpl service = new RoomServiceImpl(
                floorRepository,
                roomRepository,
                reservationRepository,
                feedbackRepository,
                amenityRepository,
                roomImageRepository,
                cloudinary,
                academicScheduleService,
                floorDecorationRepository
        );

        when(roomRepository.findById(anyString())).thenReturn(Optional.empty());

        CustomException ex = assertThrows(CustomException.class, () -> service.getRoomDetail("R404"));
        assertEquals(ResponseCode.ROOM_NOT_FOUND, ex.getResponseCode());
    }
}
