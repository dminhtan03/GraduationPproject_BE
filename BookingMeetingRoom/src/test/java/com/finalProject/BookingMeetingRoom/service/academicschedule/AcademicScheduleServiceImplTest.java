package com.finalProject.BookingMeetingRoom.service.academicschedule;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.AcademicScheduleUpdateRequest;
import com.finalProject.BookingMeetingRoom.repository.AcademicScheduleRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AcademicScheduleServiceImplTest {

    @Mock
    private AcademicScheduleRepository scheduleRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AcademicScheduleServiceImpl scheduleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // --- TEST FOR CREATE SCHEDULE ---

    @Test
    void createSchedule_Success_Normal() {
        // N: Create schedule with valid data
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        room.setLocationCode("R101");
        
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .daysOfWeek(Arrays.asList("MONDAY", "WEDNESDAY"))
                .fromDate(LocalDate.of(2026, 4, 1))
                .toDate(LocalDate.of(2026, 6, 30))
                .description("Học phần Java")
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        scheduleService.createSchedule(request);

        ArgumentCaptor<AcademicSchedule> captor = ArgumentCaptor.forClass(AcademicSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        AcademicSchedule saved = captor.getValue();

        assertEquals(room, saved.getRoom());
        assertEquals("MONDAY,WEDNESDAY", saved.getDaysOfWeek());
        assertEquals(LocalTime.of(8, 0), saved.getStartTime());
        assertNotNull(saved.getId());
    }

    @Test
    void createSchedule_RoomNotFound_Abnormal() {
        // A: Create schedule with non-existent room ID
        String roomId = "non-existent";
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> scheduleService.createSchedule(request));
        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    void createSchedule_AllDaysOfWeek_Boundary() {
        // B: Create schedule with all days of week
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        room.setLocationCode("R101");

        List<String> allDays = Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(7, 0))
                .endTime(LocalTime.of(22, 0))
                .daysOfWeek(allDays)
                .fromDate(LocalDate.of(2026, 1, 1))
                .toDate(LocalDate.of(2026, 12, 31))
                .description("Lịch học cả năm")
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        scheduleService.createSchedule(request);

        ArgumentCaptor<AcademicSchedule> captor = ArgumentCaptor.forClass(AcademicSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY", captor.getValue().getDaysOfWeek());
    }

    @Test
    void createSchedule_VeryLongDescription_Boundary() {
        // B: Create schedule with very long description
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        room.setLocationCode("R101");

        String longDesc = "A".repeat(1000);
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(13, 0))
                .endTime(LocalTime.of(15, 0))
                .daysOfWeek(Arrays.asList("FRIDAY"))
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusMonths(1))
                .description(longDesc)
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        scheduleService.createSchedule(request);

        ArgumentCaptor<AcademicSchedule> captor = ArgumentCaptor.forClass(AcademicSchedule.class);
        verify(scheduleRepository).save(captor.capture());
        assertEquals(longDesc, captor.getValue().getDescription());
    }

    @Test
    void createSchedule_DatabaseError_Abnormal() {
        // A: Database error during save
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        room.setLocationCode("R101");
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(9, 0))
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7))
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        doThrow(new RuntimeException("DB Error")).when(scheduleRepository).save(any());

        assertThrows(RuntimeException.class, () -> scheduleService.createSchedule(request));
    }

    @Test
    void createSchedule_StartTimeAfterEndTime_Abnormal() {
        // A: Start time is after end time (Logic error)
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(17, 0))
                .endTime(LocalTime.of(8, 0)) // End before start
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(7))
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        scheduleService.createSchedule(request);

        verify(scheduleRepository).save(any(AcademicSchedule.class));
    }

    @Test
    void createSchedule_FromDateAfterToDate_Abnormal() {
        // A: From date is after to date (Logic error)
        String roomId = "room-123";
        Room room = new Room();
        room.setId(roomId);
        
        AcademicScheduleCreateRequest request = AcademicScheduleCreateRequest.builder()
                .roomId(roomId)
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now().plusDays(10))
                .toDate(LocalDate.now()) // End before start
                .build();

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        scheduleService.createSchedule(request);

        verify(scheduleRepository).save(any(AcademicSchedule.class));
    }

    // --- TEST FOR UPDATE SCHEDULE ---

    @Test
    void updateSchedule_Success_Normal() {
        // N: Update schedule with valid data
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder()
                .id(scheduleId)
                .description("Old Desc")
                .build();
        
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(12, 0))
                .daysOfWeek(Arrays.asList("TUESDAY", "THURSDAY"))
                .fromDate(LocalDate.of(2026, 5, 1))
                .toDate(LocalDate.of(2026, 7, 31))
                .description("New Desc")
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        verify(scheduleRepository).save(existing);
        assertEquals("TUESDAY,THURSDAY", existing.getDaysOfWeek());
        assertEquals("New Desc", existing.getDescription());
        assertEquals(LocalTime.of(10, 0), existing.getStartTime());
    }

    @Test
    void updateSchedule_NotFound_Abnormal() {
        // A: Update non-existent schedule
        String scheduleId = "non-existent";
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder().build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> scheduleService.updateSchedule(scheduleId, request));
        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());
        assertEquals("Internal server error", exception.getMessage());
    }

    @Test
    void updateSchedule_MinimalDates_Boundary() {
        // B: Update with minimal dates
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder().id(scheduleId).build();
        
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(0, 0))
                .endTime(LocalTime.of(23, 59))
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.of(1970, 1, 1))
                .toDate(LocalDate.of(1970, 1, 1))
                .description("Minimal date test")
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        verify(scheduleRepository).save(existing);
        assertEquals(LocalDate.of(1970, 1, 1), existing.getFromDate());
    }

    @Test
    void updateSchedule_MaximalDays_Boundary() {
        // B: Update with all days of week
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder().id(scheduleId).build();
        List<String> allDays = Arrays.asList("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY");

        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(0, 0))
                .endTime(LocalTime.of(23, 59))
                .daysOfWeek(allDays)
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusYears(1))
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        assertEquals("MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY,SATURDAY,SUNDAY", existing.getDaysOfWeek());
    }

    @Test
    void updateSchedule_NullDescription_Normal() {
        // N: Update with null description
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder().id(scheduleId).description("Has Desc").build();
        
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(10, 0))
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(1))
                .description(null)
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        assertNull(existing.getDescription());
    }

    @Test
    void updateSchedule_StartTimeAfterEndTime_Abnormal() {
        // A: Start time after end time
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder().id(scheduleId).build();
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(22, 0))
                .endTime(LocalTime.of(1, 0)) // Logic error
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now())
                .toDate(LocalDate.now().plusDays(1))
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        verify(scheduleRepository).save(existing);
    }

    @Test
    void updateSchedule_FromDateAfterToDate_Abnormal() {
        // A: From date after to date
        String scheduleId = "sched-456";
        AcademicSchedule existing = AcademicSchedule.builder().id(scheduleId).build();
        AcademicScheduleUpdateRequest request = AcademicScheduleUpdateRequest.builder()
                .startTime(LocalTime.of(8, 0))
                .endTime(LocalTime.of(10, 0))
                .daysOfWeek(Arrays.asList("MONDAY"))
                .fromDate(LocalDate.now().plusMonths(1))
                .toDate(LocalDate.now()) // Logic error
                .build();

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(existing));

        scheduleService.updateSchedule(scheduleId, request);

        verify(scheduleRepository).save(existing);
    }

    // --- TEST FOR DELETE SCHEDULE ---

    @Test
    void deleteSchedule_Success_Normal() {
        // N: Delete existing schedule
        String scheduleId = "sched-789";
        doNothing().when(scheduleRepository).deleteById(scheduleId);

        scheduleService.deleteSchedule(scheduleId);

        verify(scheduleRepository, times(1)).deleteById(scheduleId);
    }

    @Test
    void deleteSchedule_NonExistent_Abnormal() {
        // A: Delete non-existent schedule
        String scheduleId = "non-existent";
        // Mocking repository to do nothing or throw depending on how deleteById is implemented
        // Usually JpaRepository.deleteById doesn't throw if ID not found, but we can mock behavior
        doNothing().when(scheduleRepository).deleteById(scheduleId);

        scheduleService.deleteSchedule(scheduleId);

        verify(scheduleRepository).deleteById(scheduleId);
    }

    @Test
    void deleteSchedule_NullId_Boundary() {
        // B: Delete with null ID
        scheduleService.deleteSchedule(null);
        verify(scheduleRepository).deleteById(null);
    }

    @Test
    void deleteSchedule_EmptyId_Boundary() {
        // B: Delete with empty string ID
        scheduleService.deleteSchedule("");
        verify(scheduleRepository).deleteById("");
    }

    @Test
    void deleteSchedule_DatabaseError_Abnormal() {
        // A: Database error during delete
        String scheduleId = "sched-789";
        doThrow(new RuntimeException("Delete Failed")).when(scheduleRepository).deleteById(scheduleId);

        assertThrows(RuntimeException.class, () -> scheduleService.deleteSchedule(scheduleId));
    }
}
