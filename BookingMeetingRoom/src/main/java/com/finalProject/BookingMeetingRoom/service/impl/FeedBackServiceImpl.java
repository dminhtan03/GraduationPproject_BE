package com.finalProject.BookingMeetingRoom.service.impl;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.LoggerFactory;

import org.slf4j.Logger;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.mapper.FeedbackMapper;
import com.finalProject.BookingMeetingRoom.model.entity.Feedback;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackRequest;
import com.finalProject.BookingMeetingRoom.model.response.FeedbackResponse;
import com.finalProject.BookingMeetingRoom.repository.FeedbackRepository;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.repository.UserRepository;
import com.finalProject.BookingMeetingRoom.service.FeedBackService;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedBackServiceImpl implements FeedBackService {
    private final Logger logger = LoggerFactory.getLogger(FeedBackServiceImpl.class);
    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final FeedbackMapper feedbackMapper;

    /**
     * Retrieves feedback for a specific seat with pagination.
     *
     * @param roomId   the ID of the room
     * @param pageNum  the page number to retrieve
     * @param pageSize the number of items per page
     * @return a paginated list of feedback responses
     */
    @Override
    public Page<FeedbackResponse> getFeedbackOfARoom(String roomId, int pageNum, int pageSize) {
        try {
            Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by("created_at").descending());

            Page<Feedback> feedbacks = feedbackRepository.findAllFeedbackOfARoom(roomId, pageable);

            return feedbacks.map(feedback -> {
                FeedbackResponse response = feedbackMapper.toFeedbackResponse(feedback);

                String firstName = Optional.ofNullable(feedback.getReservation().getUser().getUserInfo().getFirstName()).orElse("");
                String lastName = Optional.ofNullable(feedback.getReservation().getUser().getUserInfo().getLastName()).orElse("");
                response.setUserName((firstName + " " + lastName).trim());

                return response;
            });
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public void createFeedback(FeedbackRequest request, Authentication connectedUser) {
        try {
            var user = userRepository.findByEmail(connectedUser.getName());
            if (user.isEmpty()) {
                throw new CustomException(ResponseCode.USER_NOT_FOUND);
            }
            var reservation = reservationRepository.findById(request.getReservationId())
                    .orElseThrow(() -> new CustomException(ResponseCode.RESERVATION_NOT_FOUND));

            // if(!"COMPLETED".equals(reservation.getStatus())) {
            // throw new
            // CustomException(ResponseCode.FEEDBACK_ONLY_FOR_COMPLETED_RESERVATION);
            // }

            if (reservation.getFeedback() != null) {
                throw new CustomException(ResponseCode.FEEDBACK_ALREADY_EXISTS);
            }
            var feedback = new Feedback();
            feedback.setId(UUID.randomUUID().toString());
            feedback.setReservation(reservation);
            feedback.setRating(request.getRating());
            feedback.setDescription(request.getDescription());
            feedback.setCreatedAt(request.getCreatedAt() != null ? request.getCreatedAt() : LocalDateTime.now());
            feedbackRepository.save(feedback);
            reservation.setFeedback(feedback);

            var room = reservation.getRoom();

            List<Reservation> reservations = room.getReservations();

            double avgScore = reservations.stream()
                    .map(Reservation::getFeedback)
                    .filter(f -> f != null)
                    .mapToInt(Feedback::getRating)
                    .average()
                    .orElse(0.0);

            avgScore = Math.round(avgScore * 10.0) / 10.0;

            // lưu vào DB
            room.setScore(avgScore);
            roomRepository.save(room);

        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR);
        }

    }

}
