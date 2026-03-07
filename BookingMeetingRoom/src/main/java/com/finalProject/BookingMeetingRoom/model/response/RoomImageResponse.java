package com.finalProject.BookingMeetingRoom.model.response;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomImageResponse {
    private String id;
    private String roomId;
    private String imageUrl;
    private String publicId;
    private LocalDateTime createdAt;
}