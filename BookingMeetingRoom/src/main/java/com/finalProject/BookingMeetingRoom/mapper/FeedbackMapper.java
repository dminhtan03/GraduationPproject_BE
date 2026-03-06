package com.finalProject.BookingMeetingRoom.mapper;

import com.finalProject.BookingMeetingRoom.model.entity.Feedback;
import com.finalProject.BookingMeetingRoom.model.response.FeedbackResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface FeedbackMapper {
    FeedbackResponse toFeedbackResponse(Feedback feedback);
}
