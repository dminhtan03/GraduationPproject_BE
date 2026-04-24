package com.finalProject.BookingMeetingRoom.service.academicschedule;

import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;
import com.finalProject.BookingMeetingRoom.repository.AcademicScheduleRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.impl.AcademicScheduleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicScheduleServiceImplTest {

    @Mock
    private AcademicScheduleRepository scheduleRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AcademicScheduleServiceImpl service;

    @Test
    void isRoomBusyWithLearning_shouldReturnFalse_whenNoScheduleMatches() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 24, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 24, 10, 0);

        when(scheduleRepository.findSchedulesByRoomAndDate("room-1", LocalDate.of(2026, 4, 24)))
                .thenReturn(List.of());

        boolean busy = service.isRoomBusyWithLearning("room-1", start, end);

        assertFalse(busy);
    }

    @Test
    void isRoomBusyWithLearning_shouldReturnTrue_whenDayAndTimeOverlap() {
        LocalDateTime start = LocalDateTime.of(2026, 4, 24, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 4, 24, 10, 0);

        AcademicSchedule schedule = new AcademicSchedule();
        schedule.setDaysOfWeek("FRIDAY");
        schedule.setStartTime(LocalTime.of(8, 0));
        schedule.setEndTime(LocalTime.of(11, 0));

        when(scheduleRepository.findSchedulesByRoomAndDate("room-1", LocalDate.of(2026, 4, 24)))
                .thenReturn(List.of(schedule));

        boolean busy = service.isRoomBusyWithLearning("room-1", start, end);

        assertTrue(busy);
    }
}
