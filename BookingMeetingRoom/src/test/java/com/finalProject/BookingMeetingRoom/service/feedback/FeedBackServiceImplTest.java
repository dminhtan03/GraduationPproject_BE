package com.finalProject.BookingMeetingRoom.service.feedback;

import com.finalProject.BookingMeetingRoom.service.impl.FeedBackServiceImpl;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedBackServiceImplTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private FeedbackMapper feedbackMapper;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private FeedBackServiceImpl feedBackService;

    @Test
    void getFeedbackOfARoom_shouldReturnMappedPageWithUserName() {
        UserInfo userInfo = new UserInfo();
        userInfo.setFirstName("Tan");
        userInfo.setLastName("Minh");

        User user = new User();
        user.setUserInfo(userInfo);

        Reservation reservation = new Reservation();
        reservation.setUser(user);

        Feedback feedback = new Feedback();
        feedback.setReservation(reservation);

        FeedbackResponse mapped = new FeedbackResponse();
        mapped.setDescription("Good");

        Page<Feedback> feedbackPage = new PageImpl<>(List.of(feedback));

        when(feedbackRepository.findAllFeedbackOfARoom(eq("room-1"), any(Pageable.class))).thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(feedback)).thenReturn(mapped);

        Page<FeedbackResponse> result = feedBackService.getFeedbackOfARoom("room-1", 0, 10);

        assertEquals(1, result.getTotalElements());
        assertEquals("Tan Minh", result.getContent().get(0).getUserName());
        verify(feedbackRepository).findAllFeedbackOfARoom(eq("room-1"), any(Pageable.class));
    }

    @Test
    void getFeedbackOfARoom_shouldHandleNullFirstNameAndLastName() {
        UserInfo userInfo = new UserInfo();
        userInfo.setFirstName(null);
        userInfo.setLastName(null);

        User user = new User();
        user.setUserInfo(userInfo);

        Reservation reservation = new Reservation();
        reservation.setUser(user);

        Feedback feedback = new Feedback();
        feedback.setReservation(reservation);

        FeedbackResponse mapped = new FeedbackResponse();
        Page<Feedback> feedbackPage = new PageImpl<>(List.of(feedback));

        when(feedbackRepository.findAllFeedbackOfARoom(eq("room-1"), any(Pageable.class))).thenReturn(feedbackPage);
        when(feedbackMapper.toFeedbackResponse(feedback)).thenReturn(mapped);

        Page<FeedbackResponse> result = feedBackService.getFeedbackOfARoom("room-1", 0, 10);

        assertEquals("", result.getContent().get(0).getUserName());
    }

    @Test
    void getFeedbackOfARoom_shouldThrowInternalServerError_whenUnexpectedError() {
        doThrow(new RuntimeException("db fail"))
                .when(feedbackRepository).findAllFeedbackOfARoom(eq("room-1"), any(Pageable.class));

        CustomException ex = assertThrows(
                CustomException.class,
                () -> feedBackService.getFeedbackOfARoom("room-1", 0, 10)
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }

    @Test
    void createFeedback_shouldThrowUserNotFound_whenConnectedUserMissing() {
        when(authentication.getName()).thenReturn("user@mail.com");
        when(userRepository.findByEmail("user@mail.com")).thenReturn(Optional.empty());

        FeedbackRequest request = FeedbackRequest.builder()
                .reservationId("res-1")
                .rating(4)
                .description("Nice")
                .build();

        CustomException ex = assertThrows(
                CustomException.class,
                () -> feedBackService.createFeedback(request, authentication)
        );

        assertEquals(ResponseCode.USER_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createFeedback_shouldThrowReservationNotFound_whenReservationMissing() {
        User user = new User();
        user.setId("u1");

        when(authentication.getName()).thenReturn("user@mail.com");
        when(userRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.empty());

        FeedbackRequest request = FeedbackRequest.builder()
                .reservationId("res-1")
                .rating(4)
                .description("Nice")
                .build();

        CustomException ex = assertThrows(
                CustomException.class,
                () -> feedBackService.createFeedback(request, authentication)
        );

        assertEquals(ResponseCode.RESERVATION_NOT_FOUND, ex.getResponseCode());
    }

    @Test
    void createFeedback_shouldThrowFeedbackAlreadyExists_whenReservationAlreadyHasFeedback() {
        User user = new User();
        user.setId("u1");

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setFeedback(new Feedback());

        when(authentication.getName()).thenReturn("user@mail.com");
        when(userRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        FeedbackRequest request = FeedbackRequest.builder()
                .reservationId("res-1")
                .rating(4)
                .description("Nice")
                .build();

        CustomException ex = assertThrows(
                CustomException.class,
                () -> feedBackService.createFeedback(request, authentication)
        );

        assertEquals(ResponseCode.FEEDBACK_ALREADY_EXISTS, ex.getResponseCode());
    }

    @Test
    void createFeedback_shouldSaveFeedbackAndUpdateRoomScore_whenSuccess() {
        User user = new User();
        user.setId("u1");

        Room room = new Room();
        room.setId("room-1");
        room.setReservations(new ArrayList<>());

        Reservation reservation = new Reservation();
        reservation.setId("res-1");
        reservation.setRoom(room);
        reservation.setFeedback(null);

        Reservation anotherReservation = new Reservation();
        Feedback anotherFeedback = new Feedback();
        anotherFeedback.setRating(4);
        anotherReservation.setFeedback(anotherFeedback);

        room.getReservations().add(reservation);
        room.getReservations().add(anotherReservation);

        when(authentication.getName()).thenReturn("user@mail.com");
        when(userRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById("res-1")).thenReturn(Optional.of(reservation));

        FeedbackRequest request = FeedbackRequest.builder()
                .reservationId("res-1")
                .rating(5)
                .description("Very good")
                .createdAt(LocalDateTime.of(2026, 4, 20, 14, 0))
                .build();

        feedBackService.createFeedback(request, authentication);

        ArgumentCaptor<Feedback> feedbackCaptor = ArgumentCaptor.forClass(Feedback.class);
        verify(feedbackRepository).save(feedbackCaptor.capture());
        assertEquals(5, feedbackCaptor.getValue().getRating());
        assertEquals("Very good", feedbackCaptor.getValue().getDescription());
        assertEquals(LocalDateTime.of(2026, 4, 20, 14, 0), feedbackCaptor.getValue().getCreatedAt());

        verify(roomRepository).save(room);
        assertEquals(4.5, room.getScore());
    }

    @Test
    void createFeedback_shouldThrowInternalServerError_whenUnexpectedException() {
        User user = new User();
        user.setId("u1");

        Room room = new Room();
        room.setId("room-9");
        room.setReservations(new ArrayList<>());

        Reservation reservation = new Reservation();
        reservation.setId("res-9");
        reservation.setRoom(room);

        room.getReservations().add(reservation);

        when(authentication.getName()).thenReturn("user@mail.com");
        when(userRepository.findByEmail("user@mail.com")).thenReturn(Optional.of(user));
        when(reservationRepository.findById("res-9")).thenReturn(Optional.of(reservation));
        doThrow(new RuntimeException("db fail")).when(roomRepository).save(room);

        FeedbackRequest request = FeedbackRequest.builder()
                .reservationId("res-9")
                .rating(3)
                .description("ok")
                .build();

        CustomException ex = assertThrows(
                CustomException.class,
                () -> feedBackService.createFeedback(request, authentication)
        );

        assertEquals(ResponseCode.INTERNAL_SERVER_ERROR, ex.getResponseCode());
    }
}


