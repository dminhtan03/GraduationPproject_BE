package com.finalProject.BookingMeetingRoom.service.feedback;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.FeedbackMapper;
import com.finalProject.BookingMeetingRoom.model.entity.Feedback;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.entity.UserInfo;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackRequest;
import com.finalProject.BookingMeetingRoom.model.response.FeedbackResponse;
import com.finalProject.BookingMeetingRoom.repository.FeedbackRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.impl.FeedBackServiceImpl;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceImplTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private FeedbackMapper feedbackMapper;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private Authentication connectedUser;

    @Mock
    private Logger logger;

    @InjectMocks
    private FeedBackServiceImpl feedbackService;

    private User testUser;
    private UserInfo testUserInfo;
    private Room testRoom;
    private Reservation testReservation;
    private Feedback testFeedback;
    private FeedbackRequest feedbackRequest;
    private FeedbackResponse feedbackResponse;

    @BeforeEach
    void setUp() {
        // Setup UserInfo
        testUserInfo = new UserInfo();
        testUserInfo.setFirstName("John");
        testUserInfo.setLastName("Doe");
        testUserInfo.setEmail("test@example.com");

        // Setup User
        testUser = new User();
        testUser.setId("user-123");
        testUser.setUserInfo(testUserInfo);

        // Setup Room
        testRoom = new Room();
        testRoom.setId("room-123");
        testRoom.setScore(85.5);
        testRoom.setUpdatedAt(LocalDateTime.now());

        // Setup Reservation
        testReservation = new Reservation();
        testReservation.setId("reservation-123");
        testReservation.setUser(testUser);
        testReservation.setRoom(testRoom);
        testReservation.setFeedback(null); // No existing feedback

        // Setup Feedback
        testFeedback = new Feedback();
        testFeedback.setId("feedback-123");
        testFeedback.setRating(5);
        testFeedback.setDescription("Great room!");
        testFeedback.setReservation(testReservation);
        testFeedback.setCreatedAt(LocalDateTime.now());

        // Setup FeedbackRequest
        feedbackRequest = new FeedbackRequest();
        feedbackRequest.setReservationId("reservation-123");
        feedbackRequest.setRating(5);
        feedbackRequest.setDescription("Great room!");

        // Setup FeedbackResponse
        feedbackResponse = new FeedbackResponse();
        feedbackResponse.setId(UUID.randomUUID());
        feedbackResponse.setRating(5);
        feedbackResponse.setDescription("Great room!");
    }

    // ==================== getFeedbackOfARoom Tests ====================

    /**
     * Test successful getFeedbackOfARoom with data
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithData() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Pageable expectedPageable = PageRequest.of(pageNum, pageSize, Sort.by("created_at").descending());
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, expectedPageable, 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback)).thenReturn(feedbackResponse);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());

        FeedbackResponse response = result.getContent().get(0);
        assertEquals(5, response.getRating());
        assertEquals("Great room!", response.getDescription());
        assertEquals("John Doe", response.getUserName()); // First + Last name

        verify(feedbackRepository, times(1)).findAllFeedbackOfARoom(eq(roomId), any(Pageable.class));
        verify(feedbackMapper, times(1)).toFeedbackResponse(testFeedback);
    }

    /**
     * Test getFeedbackOfARoom with empty results
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithEmptyResults() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        Page<Feedback> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum, pageSize), 0);
        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(emptyPage);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        assertNotNull(result);
        assertEquals(0, result.getTotalElements());
        assertEquals(0, result.getContent().size());

        verify(feedbackRepository, times(1)).findAllFeedbackOfARoom(eq(roomId), any(Pageable.class));
        verify(feedbackMapper, never()).toFeedbackResponse(any());
    }

    /**
     * Test getFeedbackOfARoom with null first name
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithNullFirstName() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        testUserInfo.setFirstName(null);
        testUserInfo.setLastName("Doe");

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(pageNum, pageSize), 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback)).thenReturn(feedbackResponse);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        FeedbackResponse response = result.getContent().get(0);
        assertEquals("Doe", response.getUserName()); // Only last name, trimmed

        verify(feedbackMapper, times(1)).toFeedbackResponse(testFeedback);
    }

    /**
     * Test getFeedbackOfARoom with null last name
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithNullLastName() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        testUserInfo.setFirstName("John");
        testUserInfo.setLastName(null);

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(pageNum, pageSize), 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback)).thenReturn(feedbackResponse);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        FeedbackResponse response = result.getContent().get(0);
        assertEquals("John", response.getUserName()); // Only first name, trimmed

        verify(feedbackMapper, times(1)).toFeedbackResponse(testFeedback);
    }

    /**
     * Test getFeedbackOfARoom with both names null
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithBothNamesNull() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        testUserInfo.setFirstName(null);
        testUserInfo.setLastName(null);

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(pageNum, pageSize), 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback)).thenReturn(feedbackResponse);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        FeedbackResponse response = result.getContent().get(0);
        assertEquals("", response.getUserName()); // Empty string after trim

        verify(feedbackMapper, times(1)).toFeedbackResponse(testFeedback);
    }

    /**
     * Test getFeedbackOfARoom with different page parameters
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithDifferentPageParameters() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 2;
        int pageSize = 20;

        Page<Feedback> feedbackPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(pageNum, pageSize), 0);
        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);

        // Assert
        assertNotNull(result);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedbackRepository, times(1)).findAllFeedbackOfARoom(eq(roomId), pageableCaptor.capture());

        Pageable capturedPageable = pageableCaptor.getValue();
        assertEquals(pageNum, capturedPageable.getPageNumber());
        assertEquals(pageSize, capturedPageable.getPageSize());
        assertEquals(Sort.by("created_at").descending(), capturedPageable.getSort());
    }

    /**
     * Test getFeedbackOfARoom when repository throws exception
     */
    @Test
    void testGetFeedbackOfARoom_RepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(feedbackRepository, times(1)).findAllFeedbackOfARoom(eq(roomId), any(Pageable.class));
        verify(logger, times(1)).error(anyString());
    }

    /**
     * Test getFeedbackOfARoom when mapper throws exception
     */
    @Test
    void testGetFeedbackOfARoom_MapperThrowsException_ThrowsInternalServerError() {
        // Arrange
        String roomId = "room-123";
        int pageNum = 0;
        int pageSize = 10;

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(pageNum, pageSize), 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq(roomId), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback))
                .thenThrow(new RuntimeException("Mapping error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.getFeedbackOfARoom(roomId, pageNum, pageSize);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(feedbackMapper, times(1)).toFeedbackResponse(testFeedback);
        verify(logger, times(1)).error(anyString());
    }

    // ==================== addFeedback Tests ====================

    /**
     * Test successful addFeedback
     */
    @Test
    void testAddFeedback_Success() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(testFeedback);
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        // Act
        feedbackService.createFeedback(feedbackRequest, connectedUser);

        // Assert
        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, times(1)).save(feedbackCaptor.capture());

        Feedback savedFeedback = feedbackCaptor.getValue();
        assertNotNull(savedFeedback.getId());
        assertEquals(5, savedFeedback.getRating());
        assertEquals("Great room!", savedFeedback.getDescription());
        assertEquals(testReservation, savedFeedback.getReservation());
        assertNotNull(savedFeedback.getCreatedAt());

        ArgumentCaptor<Room> roomCaptor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository, times(1)).save(roomCaptor.capture());

        Room updatedRoom = roomCaptor.getValue();

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findById("reservation-123");
    }

    /**
     * Test addFeedback when user is not found
     */
    @Test
    void testAddFeedback_UserNotFound_ThrowsCustomException() {
        // Arrange
        when(connectedUser.getName()).thenReturn("nonexistent@example.com");
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("nonexistent@example.com");
        verify(reservationRepository, never()).findById(anyString());
        verify(feedbackRepository, never()).save(any());
    }

    /**
     * Test addFeedback when reservation is not found
     */
    @Test
    void testAddFeedback_ReservationNotFound_ThrowsCustomException() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("nonexistent-reservation")).thenReturn(Optional.empty());

        FeedbackRequest invalidRequest = new FeedbackRequest();
        invalidRequest.setReservationId("nonexistent-reservation");
        invalidRequest.setRating(5);
        invalidRequest.setDescription("Great room!");

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(invalidRequest, connectedUser);
        });

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findById("nonexistent-reservation");
        verify(feedbackRepository, never()).save(any());
    }

    /**
     * Test addFeedback when feedback already exists
     */
    @Test
    void testAddFeedback_FeedbackAlreadyExists_ThrowsCustomException() {
        // Arrange
        Feedback existingFeedback = new Feedback();
        testReservation.setFeedback(existingFeedback); // Reservation already has feedback

        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.FEEDBACK_ALREADY_EXISTS, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail("test@example.com");
        verify(reservationRepository, times(1)).findById("reservation-123");
        verify(feedbackRepository, never()).save(any());
        verify(roomRepository, never()).save(any());
    }

    /**
     * Test addFeedback when feedback repository throws exception
     */
    @Test
    void testAddFeedback_FeedbackRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));
        when(feedbackRepository.save(any(Feedback.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(feedbackRepository, times(1)).save(any(Feedback.class));
        verify(logger, times(1)).error(anyString());
    }

    /**
     * Test addFeedback when room repository throws exception
     */
    @Test
    void testAddFeedback_RoomRepositoryThrowsException_ThrowsInternalServerError() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(testFeedback);
        when(roomRepository.save(any(Room.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, exception.getResponseCode());

        verify(feedbackRepository, times(1)).save(any(Feedback.class));
        verify(roomRepository, times(1)).save(any(Room.class));
        verify(logger, times(1)).error(anyString());
    }

    /**
     * Test addFeedback when CustomException is thrown - should be re-thrown
     */
    @Test
    void testAddFeedback_CustomExceptionThrown_ReThrowsCustomException() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));

        CustomException customException = new CustomException(ResponseCode.ROOM_NOT_FOUND);
        when(feedbackRepository.save(any(Feedback.class))).thenThrow(customException);

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.ROOM_NOT_FOUND, exception.getResponseCode());
        assertSame(customException, exception);

        verify(feedbackRepository, times(1)).save(any(Feedback.class));
        verify(logger, never()).error(anyString()); // Logger not called for CustomException
    }

    /**
     * Test addFeedback with null authentication name
     */
    @Test
    void testAddFeedback_NullAuthenticationName_ThrowsUserNotFound() {
        // Arrange
        when(connectedUser.getName()).thenReturn(null);
        when(userRepository.findByEmail(null)).thenReturn(Optional.empty());

        // Act & Assert
        CustomException exception = assertThrows(CustomException.class, () -> {
            feedbackService.createFeedback(feedbackRequest, connectedUser);
        });

        assertEquals(ResponseCode.USER_NOT_FOUND, exception.getResponseCode());

        verify(userRepository, times(1)).findByEmail(null);
    }

    /**
     * Test UUID generation in addFeedback
     */
    @Test
    void testAddFeedback_GeneratesValidUUID() {
        // Arrange
        when(connectedUser.getName()).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(reservationRepository.findById("reservation-123")).thenReturn(Optional.of(testReservation));
        when(feedbackRepository.save(any(Feedback.class))).thenReturn(testFeedback);
        when(roomRepository.save(any(Room.class))).thenReturn(testRoom);

        // Act
        feedbackService.createFeedback(feedbackRequest, connectedUser);

        // Assert
        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository, times(1)).save(feedbackCaptor.capture());

        Feedback savedFeedback = feedbackCaptor.getValue();
        assertNotNull(savedFeedback.getId());
        // Verify it's a valid UUID format
        assertDoesNotThrow(() -> UUID.fromString(savedFeedback.getId()));
    }

    // ==================== Integration Tests ====================

    /**
     * Test complete feedback flow with multiple feedbacks
     */
    @Test
    void testCompleteFeedbackFlow_Success() {
        // Arrange - Create multiple feedbacks
        Feedback feedback1 = new Feedback();
        feedback1.setReservation(testReservation);

        Feedback feedback2 = new Feedback();
        User user2 = new User();
        UserInfo userInfo2 = new UserInfo();
        userInfo2.setFirstName("Jane");
        userInfo2.setLastName("Smith");
        user2.setUserInfo(userInfo2);
        Reservation reservation2 = new Reservation();
        reservation2.setUser(user2);
        feedback2.setReservation(reservation2);

        List<Feedback> feedbacks = Arrays.asList(feedback1, feedback2);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(0, 10), 2);

        FeedbackResponse response1 = new FeedbackResponse();
        FeedbackResponse response2 = new FeedbackResponse();

        when(feedbackRepository.findAllFeedbackOfARoom(eq("room-123"), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(feedback1)).thenReturn(response1);
        when(feedbackMapper.toFeedbackResponse(feedback2)).thenReturn(response2);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom("room-123", 0, 10);

        // Assert
        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());

        // Verify user names are set correctly
        assertEquals("John Doe", result.getContent().get(0).getUserName());
        assertEquals("Jane Smith", result.getContent().get(1).getUserName());

        verify(feedbackMapper, times(2)).toFeedbackResponse(any(Feedback.class));
    }

    /**
     * Test edge case with empty/whitespace names
     */
    @Test
    void testGetFeedbackOfARoom_Success_WithWhitespaceNames() {
        // Arrange
        testUserInfo.setFirstName("  ");
        testUserInfo.setLastName("  ");

        List<Feedback> feedbacks = Arrays.asList(testFeedback);
        Page<Feedback> feedbackPage = new PageImpl<>(feedbacks, PageRequest.of(0, 10), 1);

        when(feedbackRepository.findAllFeedbackOfARoom(eq("room-123"), any(Pageable.class)))
                .thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(testFeedback)).thenReturn(feedbackResponse);

        // Act
        Page<FeedbackResponse> result = feedbackService.getFeedbackOfARoom("room-123", 0, 10);

        // Assert
        FeedbackResponse response = result.getContent().get(0);
        assertEquals("", response.getUserName()); // Should be empty after trim
    }
}