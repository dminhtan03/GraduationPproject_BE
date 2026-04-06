package com.finalProject.BookingMeetingRoom.service.building;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BuildingServiceImplTest {

    @Mock
    private BuildingRepository buildingRepository;

    @Mock
    private FloorRepository floorRepository;

    @Mock
    private Logger logger;

    @InjectMocks
    private BuildingServiceImpl buildingService;

    private BuildingRequest buildingRequest;
    private Building testBuilding;
    private AdminBuildingDto adminBuildingDto;
    private AdminFloorDto adminFloorDto;

    @BeforeEach
    void setUp() {
        buildingRequest = new BuildingRequest();
        buildingRequest.setName("Test Building");
        buildingRequest.setAddress("123 Test Street");

        testBuilding = new Building();
        testBuilding.setId("building-123");
        testBuilding.setName("Test Building");
        testBuilding.setAddress("123 Test Street");
        testBuilding.setDeleted(false);
        testBuilding.setCreatedAt(LocalDateTime.now());
        testBuilding.setUpdatedAt(LocalDateTime.now());

        adminBuildingDto = new AdminBuildingDto(
                testBuilding.getId(),
                testBuilding.getName(),
                testBuilding.getAddress(),
                2
        );

        adminFloorDto = new AdminFloorDto(
                "floor-123",
                "Floor 1",
                testBuilding.getId()
        );
    }

    // ==================== addBuilding Tests ====================

    /**
     * Test successful building addition
     */
    @Test
    void testAddBuilding_Success() {
        // Arrange
        when(buildingRepository.save(any(Building.class))).thenReturn(testBuilding);

        // Act
        buildingService.addBuilding(buildingRequest);

        // Assert
        ArgumentCaptor<Building> buildingCaptor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(1)).save(buildingCaptor.capture());

        Building savedBuilding = buildingCaptor.getValue();
        assertNotNull(savedBuilding.getId());
        assertEquals("Test Building", savedBuilding.getName());
        assertEquals("123 Test Street", savedBuilding.getAddress());
        assertNotNull(savedBuilding.getCreatedAt());
        assertNotNull(savedBuilding.getUpdatedAt());
    }

    /**
     * Test addBuilding when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testAddBuilding_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        CustomException customException = new CustomException(ResponseCode.BUILDING_NOT_FOUND);
        when(buildingRepository.save(any(Building.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.addBuilding(buildingRequest);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(buildingRepository, times(1)).save(any(Building.class));
        verify(logger, never()).error(anyString(), any(Exception.class)); // Logger not called for CustomException
    }

    /**
     * Test addBuilding with null request values
     */
    @Test
    void testAddBuilding_Success_WithNullValues() {
        // Arrange
        BuildingRequest nullRequest = new BuildingRequest();
        nullRequest.setName(null);
        nullRequest.setAddress(null);

        when(buildingRepository.save(any(Building.class))).thenReturn(testBuilding);

        // Act
        buildingService.addBuilding(nullRequest);

        // Assert
        ArgumentCaptor<Building> buildingCaptor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(1)).save(buildingCaptor.capture());

        Building savedBuilding = buildingCaptor.getValue();
        assertNotNull(savedBuilding.getId());
        assertNull(savedBuilding.getName());
        assertNull(savedBuilding.getAddress());
    }

    // ==================== getAllBuilding Tests ====================

    /**
     * Test successful retrieval of all buildings with data
     */
    @Test
    void testGetAllBuilding_Success_WithData() {
        // Arrange
        int pageNum = 0;
        int pageSize = 10;

        List<AdminBuildingDto> buildings = Arrays.asList(adminBuildingDto);
        Page<AdminBuildingDto> buildingPage = new PageImpl<>(buildings, PageRequest.of(pageNum, pageSize), 1);

        when(buildingRepository.findAllBuilding(any(Pageable.class))).thenReturn(buildingPage);

        // Act
        Page<AdminBuildingDto> result = buildingService.getAllBuilding(pageNum, pageSize);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
        assertEquals("building-123", result.getContent().get(0).getId());
        assertEquals("Test Building", result.getContent().get(0).getName());

        verify(buildingRepository, times(1)).findAllBuilding(any(Pageable.class));
    }

    /**
     * Test successful retrieval of buildings with empty results
     */
    @Test
    void testGetAllBuilding_Success_WithEmptyData() {
        // Arrange
        int pageNum = 0;
        int pageSize = 10;

        Page<AdminBuildingDto> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum, pageSize), 0);
        when(buildingRepository.findAllBuilding(any(Pageable.class))).thenReturn(emptyPage);

        // Act
        Page<AdminBuildingDto> result = buildingService.getAllBuilding(pageNum, pageSize);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());

        verify(buildingRepository, times(1)).findAllBuilding(any(Pageable.class));
    }

    /**
     * Test getAllBuilding with different page parameters
     */
    @Test
    void testGetAllBuilding_Success_WithDifferentPageParameters() {
        // Arrange
        int pageNum = 2;
        int pageSize = 20;

        Page<AdminBuildingDto> buildingPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum, pageSize), 0);
        when(buildingRepository.findAllBuilding(any(Pageable.class))).thenReturn(buildingPage);

        // Act
        Page<AdminBuildingDto> result = buildingService.getAllBuilding(pageNum, pageSize);

        // Assert
        assertNotNull(result);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(buildingRepository, times(1)).findAllBuilding(pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(pageNum, capturedPageable.getPageNumber());
        assertEquals(pageSize, capturedPageable.getPageSize());
    }

    // ==================== getBuildingById Tests ====================

    /**
     * Test successful building retrieval by ID with floors
     */
    @Test
    void testGetBuildingById_Success_WithFloors() {
        // Arrange
        String buildingId = "building-123";
        List<AdminFloorDto> floors = Arrays.asList(adminFloorDto);

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(floorRepository.findFloorByBuildingIdAndDeleted(buildingId)).thenReturn(floors);

        // Act
        BuildingResponse result = buildingService.getBuildingById(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals("Test Building", result.getName());
        assertEquals("123 Test Street", result.getAddress());
        assertEquals(1, result.getFloors().size());
        assertEquals("floor-123", result.getFloors().get(0).getId());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
        verify(floorRepository, times(1)).findFloorByBuildingIdAndDeleted(buildingId);
    }

    /**
     * Test successful building retrieval by ID with no floors
     */
    @Test
    void testGetBuildingById_Success_WithNoFloors() {
        // Arrange
        String buildingId = "building-123";

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(floorRepository.findFloorByBuildingIdAndDeleted(buildingId)).thenReturn(Collections.emptyList());

        // Act
        BuildingResponse result = buildingService.getBuildingById(buildingId);

        // Assert
        assertNotNull(result);
        assertEquals("Test Building", result.getName());
        assertEquals("123 Test Street", result.getAddress());
        assertEquals(0, result.getFloors().size());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
        verify(floorRepository, times(1)).findFloorByBuildingIdAndDeleted(buildingId);
    }

    /**
     * Test getBuildingById when building is not found
     */
    @Test
    void testGetBuildingById_BuildingNotFound_ThrowsCustomException() {
        // Arrange
        String buildingId = "nonexistent-building";

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.getBuildingById(buildingId);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
        verify(floorRepository, never()).findFloorByBuildingIdAndDeleted(anyString());
    }

    // ==================== updateBuilding Tests ====================

    /**
     * Test successful building update
     */
    @Test
    void testUpdateBuilding_Success() {
        // Arrange
        String buildingId = "building-123";
        BuildingRequest updateRequest = new BuildingRequest();
        updateRequest.setName("Updated Building");
        updateRequest.setAddress("456 Updated Street");

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(buildingRepository.save(any(Building.class))).thenReturn(testBuilding);

        // Act
        buildingService.updateBuilding(buildingId, updateRequest);

        // Assert
        ArgumentCaptor<Building> buildingCaptor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(1)).save(buildingCaptor.capture());

        Building updatedBuilding = buildingCaptor.getValue();
        assertEquals("Updated Building", updatedBuilding.getName());
        assertEquals("456 Updated Street", updatedBuilding.getAddress());
        assertNotNull(updatedBuilding.getUpdatedAt());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
    }

    /**
     * Test updateBuilding when building is not found
     */
    @Test
    void testUpdateBuilding_BuildingNotFound_ThrowsCustomException() {
        // Arrange
        String buildingId = "nonexistent-building";

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.updateBuilding(buildingId, buildingRequest);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
        verify(buildingRepository, never()).save(any(Building.class));
    }

    /**
     * Test updateBuilding when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testUpdateBuilding_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String buildingId = "building-123";
        CustomException customException = new CustomException(ResponseCode.BUILDING_NOT_FOUND);

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(buildingRepository.save(any(Building.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.updateBuilding(buildingId, buildingRequest);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(logger, never()).error(anyString(), any(Exception.class));
    }

    // ==================== deleteBuilding Tests ====================

    /**
     * Test successful building deletion (soft delete)
     */
    @Test
    void testDeleteBuilding_Success() {
        // Arrange
        String buildingId = "building-123";

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(buildingRepository.save(any(Building.class))).thenReturn(testBuilding);

        // Act
        buildingService.deleteBuilding(buildingId);

        // Assert
        ArgumentCaptor<Building> buildingCaptor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(1)).save(buildingCaptor.capture());

        Building deletedBuilding = buildingCaptor.getValue();
        assertNotNull(deletedBuilding.getUpdatedAt());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
    }

    /**
     * Test deleteBuilding when building is not found
     */
    @Test
    void testDeleteBuilding_BuildingNotFound_ThrowsCustomException() {
        // Arrange
        String buildingId = "nonexistent-building";

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(null);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.deleteBuilding(buildingId);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());

        verify(buildingRepository, times(1)).findByIdAndIsDeleted(buildingId, false);
        verify(buildingRepository, never()).save(any(Building.class));
    }

    /**
     * Test deleteBuilding when CustomException is thrown by repository - should be re-thrown
     */
    @Test
    void testDeleteBuilding_RepositoryThrowsCustomException_ReThrowsCustomException() {
        // Arrange
        String buildingId = "building-123";
        CustomException customException = new CustomException(ResponseCode.BUILDING_NOT_FOUND);

        when(buildingRepository.findByIdAndIsDeleted(buildingId, false)).thenReturn(testBuilding);
        when(buildingRepository.save(any(Building.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            buildingService.deleteBuilding(buildingId);
        });

        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(logger, never()).error(anyString(), any(Exception.class));
    }

    /**
     * Test with null building ID
     */
    @Test
    void testOperationsWithNullBuildingId() {
        // Arrange
        String nullBuildingId = null;

        when(buildingRepository.findByIdAndIsDeleted(nullBuildingId, false)).thenReturn(null);

        // Test getBuildingById with null ID
        CustomException exception1 = assertThrows(CustomException.class, () -> {
            buildingService.getBuildingById(nullBuildingId);
        });
        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception1.getResponseCode());

        // Test updateBuilding with null ID
        CustomException exception2 = assertThrows(CustomException.class, () -> {
            buildingService.updateBuilding(nullBuildingId, buildingRequest);
        });
        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception2.getResponseCode());

        // Test deleteBuilding with null ID
        CustomException exception3 = assertThrows(CustomException.class, () -> {
            buildingService.deleteBuilding(nullBuildingId);
        });
        assertEquals(ResponseCode.BUILDING_NOT_FOUND, exception3.getResponseCode());

        verify(buildingRepository, times(3)).findByIdAndIsDeleted(nullBuildingId, false);
    }

    /**
     * Test UUID generation in addBuilding
     */
    @Test
    void testAddBuilding_GeneratesValidUUID() {
        // Arrange
        when(buildingRepository.save(any(Building.class))).thenReturn(testBuilding);

        // Act
        buildingService.addBuilding(buildingRequest);

        // Assert
        ArgumentCaptor<Building> buildingCaptor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository, times(1)).save(buildingCaptor.capture());

        Building savedBuilding = buildingCaptor.getValue();
        assertNotNull(savedBuilding.getId());
        // Verify it's a valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(savedBuilding.getId()));
    }
}