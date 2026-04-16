package com.finalProject.BookingMeetingRoom.service.floor;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.dto.AdminFloorDto;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.projection.RoomDtoProjection;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.repository.FloorRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FloorServiceImplTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private FloorServiceImpl floorService;

    private Building testBuilding;
    private Floor testFloor;
    private FloorRequest floorRequest;
    private UpdateFloorRequest updateFloorRequest;
    private AdminFloorDto adminFloorDto;
    private RoomDtoProjection roomProjection;

    @BeforeEach
    void setUp() {
        testBuilding = new Building();
        testBuilding.setId("building-123");
        testBuilding.setName("Test Building");

        testFloor = new Floor();
        testFloor.setId("floor-123");
        testFloor.setName("Test Floor");
        testFloor.setDeleted(false);
        testFloor.setBuilding(testBuilding);
        testFloor.setCreateAt(LocalDateTime.now());
        testFloor.setUpdatedAt(LocalDateTime.now());

        floorRequest = new FloorRequest();
        floorRequest.setBuildingId("building-123");
        floorRequest.setName("New Floor");

        updateFloorRequest = new UpdateFloorRequest();
        updateFloorRequest.setName("Updated Floor");

        adminFloorDto = new AdminFloorDto(
                "floor-123",
                "Test Floor",
                "building-123"
        );

        roomProjection = new RoomDtoProjection() {
            @Override
            public String getRoomId() { return "room-123"; }
            @Override
            public String getLocationCode() { return "A1-01"; }
            @Override
            public RoomStatus getStatus() { return RoomStatus.AVAILABLE; }
            @Override
            public Double getScore() { return 85.5; }
        };
    }

    // Helper method để tạo RoomDtoProjection
    private RoomDtoProjection createRoomProjection(String roomId, String locationCode,
                                                   RoomStatus status, Double score) {
        return new RoomDtoProjection() {
            @Override
            public String getRoomId() { return roomId; }
            @Override
            public String getLocationCode() { return locationCode; }
            @Override
            public RoomStatus getStatus() { return status; }
            @Override
            public Double getScore() { return score; }
        };
    }

    // ==================== addFloor Tests ====================

    /**
     * Test successful floor addition
     */
    @Test
    void testAddFloor_Success() {
        // Arrange
        when(buildingRepository.findById("building-123")).thenReturn(Optional.of(testBuilding));
        when(floorRepository.save(any(Floor.class))).thenReturn(testFloor);

        // Act
        floorService.addFloor(floorRequest);

        // Assert
        ArgumentCaptor<Floor> floorCaptor = ArgumentCaptor.forClass(Floor.class);
        verify(floorRepository, times(1)).save(floorCaptor.capture());

        Floor savedFloor = floorCaptor.getValue();
        assertNotNull(savedFloor.getId());
        assertEquals("New Floor", savedFloor.getName());
        assertEquals(testBuilding, savedFloor.getBuilding());
        assertNotNull(savedFloor.getCreateAt());
        assertNotNull(savedFloor.getUpdatedAt());

        verify(buildingRepository, times(1)).findById("building-123");
    }

    /**
     * Test addFloor when building is not found
     */
    @Test
    void testAddFloor_BuildingNotFound_ThrowsCustomException() {
        // Arrange
        when(buildingRepository.findById("nonexistent-building")).thenReturn(Optional.empty());

        FloorRequest requestWithInvalidBuilding = new FloorRequest();
        requestWithInvalidBuilding.setBuildingId("nonexistent-building");
        requestWithInvalidBuilding.setName("New Floor");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.addFloor(requestWithInvalidBuilding);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());

        verify(buildingRepository, times(1)).findById("nonexistent-building");
        verify(floorRepository, never()).save(any(Floor.class));
    }

    /**
     * Test addFloor when repository throws RuntimeException
     */
    @Test
    void testAddFloor_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(buildingRepository.findById("building-123")).thenReturn(Optional.of(testBuilding));
        when(floorRepository.save(any(Floor.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.addFloor(floorRequest);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(buildingRepository, times(1)).findById("building-123");
        verify(floorRepository, times(1)).save(any(Floor.class));
    }

    /**
     * Test addFloor when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testAddFloor_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        when(buildingRepository.findById("building-123")).thenReturn(Optional.of(testBuilding));

        CustomException customException = new CustomException(ResponseCode.FLOOR_NOT_FOUND);
        when(floorRepository.save(any(Floor.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.addFloor(floorRequest);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(floorRepository, times(1)).save(any(Floor.class));
    }

    /**
     * Test UUID generation in addFloor
     */
    @Test
    void testAddFloor_GeneratesValidUUID() {
        // Arrange
        when(buildingRepository.findById("building-123")).thenReturn(Optional.of(testBuilding));
        when(floorRepository.save(any(Floor.class))).thenReturn(testFloor);

        // Act
        floorService.addFloor(floorRequest);

        // Assert
        ArgumentCaptor<Floor> floorCaptor = ArgumentCaptor.forClass(Floor.class);
        verify(floorRepository, times(1)).save(floorCaptor.capture());

        Floor savedFloor = floorCaptor.getValue();
        assertNotNull(savedFloor.getId());
        // Verify it's a valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(savedFloor.getId()));
    }

    // ==================== getAllFloorsOfBuilding Tests ====================

    /**
     * Test successful retrieval of floors with data
     */
    @Test
    void testGetAllFloorsOfBuilding_Success_WithData() {
        // Arrange
        String buildingId = "building-123";
        int pageNum = 0;
        int pageSize = 10;

        List<AdminFloorDto> floors = Arrays.asList(adminFloorDto);
        Pageable expectedPageable = PageRequest.of(pageNum, pageSize, Sort.by("created_at").descending());
        Page<AdminFloorDto> floorPage = new PageImpl<>(floors, expectedPageable, 1);

        when(floorRepository.findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class)))
                .thenReturn(floorPage);

        // Act
        Page<FloorResponse> result = floorService.getAllFloorsOfBuilding(buildingId, pageNum, pageSize);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        FloorResponse floorResponse = result.getContent().get(0);
        assertEquals("floor-123", floorResponse.getId());
        assertEquals("Test Floor", floorResponse.getName());
        assertEquals("building-123", floorResponse.getBuildingId());

        verify(floorRepository, times(1)).findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class));
    }

    /**
     * Test getAllFloorsOfBuilding when no floors found
     */
    @Test
    void testGetAllFloorsOfBuilding_NoFloorsFound_ThrowsCustomException() {
        // Arrange
        String buildingId = "building-123";
        int pageNum = 0;
        int pageSize = 10;

        Page<AdminFloorDto> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum, pageSize), 0);
        when(floorRepository.findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getAllFloorsOfBuilding(buildingId, pageNum, pageSize);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class));
    }

    /**
     * Test getAllFloorsOfBuilding when repository throws RuntimeException
     */
    @Test
    void testGetAllFloorsOfBuilding_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String buildingId = "building-123";
        int pageNum = 0;
        int pageSize = 10;

        when(floorRepository.findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getAllFloorsOfBuilding(buildingId, pageNum, pageSize);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class));
    }

    /**
     * Test getAllFloorsOfBuilding with different page parameters
     */
    @Test
    void testGetAllFloorsOfBuilding_Success_WithDifferentPageParameters() {
        // Arrange
        String buildingId = "building-123";
        int pageNum = 2;
        int pageSize = 20;

        List<AdminFloorDto> floors = Arrays.asList(adminFloorDto);
        Pageable expectedPageable = PageRequest.of(pageNum, pageSize, Sort.by("created_at").descending());
        Page<AdminFloorDto> floorPage = new PageImpl<>(floors, expectedPageable, 1);

        when(floorRepository.findAllByBuildingIdAndDeleted(eq(buildingId), any(Pageable.class)))
                .thenReturn(floorPage);

        // Act
        Page<FloorResponse> result = floorService.getAllFloorsOfBuilding(buildingId, pageNum, pageSize);

        // Assert
        assertNotNull(result);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(floorRepository, times(1)).findAllByBuildingIdAndDeleted(eq(buildingId), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(pageNum, capturedPageable.getPageNumber());
        assertEquals(pageSize, capturedPageable.getPageSize());
        assertEquals(Sort.by("created_at").descending(), capturedPageable.getSort());
    }

    // ==================== getFloorById Tests ====================

    /**
     * Test successful floor retrieval by ID with rooms
     */
    @Test
    void testGetFloorById_Success_WithRooms() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.of(adminFloorDto));

        List<RoomDtoProjection> rooms = Arrays.asList(
                createRoomProjection("room-1", "A1-01", RoomStatus.AVAILABLE, 85.5),
                createRoomProjection("room-2", "A1-02", RoomStatus.UNAVAILABLE, 90.0)
        );
        when(roomRepository.findRooms(floorId)).thenReturn(rooms);

        // Act
        DetailFloorResponse result = floorService.getFloorById(floorId);

        // Assert
        assertNotNull(result);
        assertEquals("Test Floor", result.getFloorName());
        assertEquals(2, result.getRooms().size());

        RoomDto room1 = result.getRooms().get(0);
        assertEquals("room-1", room1.getRoomId());
        assertEquals("A1-01", room1.getLocationCode());
        assertEquals(RoomStatus.AVAILABLE, room1.getStatus());
        assertEquals(85.5, room1.getScore());

        RoomDto room2 = result.getRooms().get(1);
        assertEquals("room-2", room2.getRoomId());
        assertEquals("A1-02", room2.getLocationCode());
        assertEquals(RoomStatus.UNAVAILABLE, room2.getStatus());
        assertEquals(90.0, room2.getScore());

        verify(floorRepository, times(1)).findFloor(floorId);
        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test successful floor retrieval by ID with no rooms
     */
    @Test
    void testGetFloorById_Success_WithNoRooms() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.of(adminFloorDto));
        when(roomRepository.findRooms(floorId)).thenReturn(Collections.emptyList());

        // Act
        DetailFloorResponse result = floorService.getFloorById(floorId);

        // Assert
        assertNotNull(result);
        assertEquals("Test Floor", result.getFloorName());
        assertEquals(0, result.getRooms().size());

        verify(floorRepository, times(1)).findFloor(floorId);
        verify(roomRepository, times(1)).findRooms(floorId);
    }

    /**
     * Test getFloorById when floor is not found
     */
    @Test
    void testGetFloorById_FloorNotFound_ThrowsCustomException() {
        // Arrange
        String floorId = "nonexistent-floor";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getFloorById(floorId);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findFloor(floorId);
        verify(roomRepository, never()).findRooms(anyString());
    }

    /**
     * Test getFloorById when repository throws RuntimeException
     */
    @Test
    void testGetFloorById_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getFloorById(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findFloor(floorId);
    }

    /**
     * Test getFloorById when room repository throws exception
     */
    @Test
    void testGetFloorById_RoomRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.of(adminFloorDto));
        when(roomRepository.findRooms(floorId))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getFloorById(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findFloor(floorId);
        verify(roomRepository, times(1)).findRooms(floorId);
    }

    // ==================== updateFloor Tests ====================

    /**
     * Test successful floor update
     */
    @Test
    void testUpdateFloor_Success() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class))).thenReturn(testFloor);

        // Act
        floorService.updateFloor(floorId, updateFloorRequest);

        // Assert
        ArgumentCaptor<Floor> floorCaptor = ArgumentCaptor.forClass(Floor.class);
        verify(floorRepository, times(1)).save(floorCaptor.capture());

        Floor updatedFloor = floorCaptor.getValue();
        assertEquals("Updated Floor", updatedFloor.getName());
        assertNotNull(updatedFloor.getUpdatedAt());

        verify(floorRepository, times(1)).findByIdAndDeleted(floorId);
    }

    /**
     * Test updateFloor when floor is not found
     */
    @Test
    void testUpdateFloor_FloorNotFound_ThrowsCustomException() {
        // Arrange
        String floorId = "nonexistent-floor";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.updateFloor(floorId, updateFloorRequest);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findByIdAndDeleted(floorId);
        verify(floorRepository, never()).save(any(Floor.class));
    }

    /**
     * Test updateFloor when repository throws RuntimeException
     */
    @Test
    void testUpdateFloor_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.updateFloor(floorId, updateFloorRequest);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).save(any(Floor.class));
    }

    /**
     * Test updateFloor when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testUpdateFloor_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String floorId = "floor-123";
        CustomException customException = new CustomException(ResponseCode.FLOOR_NOT_FOUND);

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.updateFloor(floorId, updateFloorRequest);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);
    }

    // ==================== deleteFloor Tests ====================

    /**
     * Test successful floor deletion (soft delete)
     */
    @Test
    void testDeleteFloor_Success() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class))).thenReturn(testFloor);

        // Act
        floorService.deleteFloor(floorId);

        // Assert
        ArgumentCaptor<Floor> floorCaptor = ArgumentCaptor.forClass(Floor.class);
        verify(floorRepository, times(1)).save(floorCaptor.capture());

        Floor deletedFloor = floorCaptor.getValue();
        assertNotNull(deletedFloor.getUpdatedAt());

        verify(floorRepository, times(1)).findByIdAndDeleted(floorId);
    }

    /**
     * Test deleteFloor when floor is not found
     */
    @Test
    void testDeleteFloor_FloorNotFound_ThrowsCustomException() {
        // Arrange
        String floorId = "nonexistent-floor";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.deleteFloor(floorId);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());

        verify(floorRepository, times(1)).findByIdAndDeleted(floorId);
        verify(floorRepository, never()).save(any(Floor.class));
    }

    /**
     * Test deleteFloor when repository throws RuntimeException
     */
    @Test
    void testDeleteFloor_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.deleteFloor(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).save(any(Floor.class));
    }

    /**
     * Test deleteFloor when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testDeleteFloor_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String floorId = "floor-123";
        CustomException customException = new CustomException(ResponseCode.FLOOR_NOT_FOUND);

        when(floorRepository.findByIdAndDeleted(floorId)).thenReturn(Optional.of(testFloor));
        when(floorRepository.save(any(Floor.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.deleteFloor(floorId);
        });

        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);
    }

    // ==================== Edge Cases Tests ====================

    /**
     * Test with null floor ID
     */
    @Test
    void testOperationsWithNullFloorId() {
        // Arrange
        String nullFloorId = null;

        when(floorRepository.findFloor(nullFloorId)).thenReturn(Optional.empty());
        when(floorRepository.findByIdAndDeleted(nullFloorId)).thenReturn(Optional.empty());

        // Test getFloorById with null ID
        CustomException exception1 = assertThrows(CustomException.class, () -> {
            floorService.getFloorById(nullFloorId);
        });
        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception1.getResponseCode());

        // Test updateFloor with null ID
        CustomException exception2 = assertThrows(CustomException.class, () -> {
            floorService.updateFloor(nullFloorId, updateFloorRequest);
        });
        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception2.getResponseCode());

        // Test deleteFloor with null ID
        CustomException exception3 = assertThrows(CustomException.class, () -> {
            floorService.deleteFloor(nullFloorId);
        });
        assertEquals(ResponseCode.FLOOR_NOT_FOUND, exception3.getResponseCode());

        verify(floorRepository, times(1)).findFloor(nullFloorId);
        verify(floorRepository, times(2)).findByIdAndDeleted(nullFloorId);
    }

    /**
     * Test with multiple rooms and different statuses
     */
    @Test
    void testGetFloorById_Success_WithMultipleRoomStatuses() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.of(adminFloorDto));

        List<RoomDtoProjection> rooms = Arrays.asList(
                createRoomProjection("room-1", "A1-01", RoomStatus.AVAILABLE, 85.5),
                createRoomProjection("room-2", "A1-02", RoomStatus.UNAVAILABLE, 90.0),
                createRoomProjection("room-3", "A1-03", RoomStatus.BROKEN, 75.0)
        );
        when(roomRepository.findRooms(floorId)).thenReturn(rooms);

        // Act
        DetailFloorResponse result = floorService.getFloorById(floorId);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.getRooms().size());

        // Verify all room statuses are present
        List<RoomStatus> statuses = result.getRooms().stream()
                .map(RoomDto::getStatus)
                .toList();

        assertTrue(statuses.contains(RoomStatus.AVAILABLE));
        assertTrue(statuses.contains(RoomStatus.UNAVAILABLE));
        assertTrue(statuses.contains(RoomStatus.BROKEN));
    }

    /**
     * Test stream mapping with null projection in list
     */
    @Test
    void testGetFloorById_NullProjectionInList_ThrowsInternalServerError() {
        // Arrange
        String floorId = "floor-123";

        when(floorRepository.findFloor(floorId)).thenReturn(Optional.of(adminFloorDto));

        List<RoomDtoProjection> rooms = Arrays.asList(
                roomProjection,
                null // This will cause NPE during stream processing
        );
        when(roomRepository.findRooms(floorId)).thenReturn(rooms);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            floorService.getFloorById(floorId);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(floorRepository, times(1)).findFloor(floorId);
        verify(roomRepository, times(1)).findRooms(floorId);
    }
}