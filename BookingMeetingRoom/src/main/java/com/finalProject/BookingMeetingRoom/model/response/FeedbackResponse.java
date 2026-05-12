package com.finalProject.BookingMeetingRoom.model.response;


import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class FeedbackResponse {

    private UUID id;
    
    private Integer rating;
    
    private String description;

    private String userName;

}

