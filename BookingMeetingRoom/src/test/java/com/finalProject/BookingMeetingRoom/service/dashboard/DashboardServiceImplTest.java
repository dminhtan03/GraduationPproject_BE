package com.finalProject.BookingMeetingRoom.service.dashboard;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.AcademicScheduleService;
import com.finalProject.BookingMeetingRoom.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private FloorRepository floorRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private AcademicScheduleService academicScheduleService;

    @InjectMocks
    private DashboardServiceImpl service;

    @Test
    void getAllBuildings_shouldReturnRepositoryResult() {
        when(buildingRepository.findAllBuildings()).thenReturn(List.of());

        List<AmbiguousBuildingResponse> result = service.getAllBuildings();

        assertEquals(0, result.size());
    }

    @Test
    void getAllBuildings_shouldThrowInternalServerError_whenUnexpectedException() {
        when(buildingRepository.findAllBuildings()).thenThrow(new RuntimeException("boom"));

        CustomException ex = assertThrows(CustomException.class, () -> service.getAllBuildings());

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}
