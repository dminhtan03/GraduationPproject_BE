package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.request.FeedbackInfoRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class RoomDetailResponse {

    private String id;
    private String locationCode;
    private RoomStatus status;
    private Integer capacity;

    private List<Amenity> amenities;
    private List<RoomImageResponse> images;

    private Double score;

    private String currentUserId;
    private String currentUserName;
    private LocalDateTime checkInTime;

    private List<FeedbackInfoRequest> feedbacks;
}
