package com.finalProject.BookingMeetingRoom.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomImageResponse {
    private String id;
    private String imageUrl;
    private String publicId;
    private LocalDateTime createdAt;
    private String roomId;
}