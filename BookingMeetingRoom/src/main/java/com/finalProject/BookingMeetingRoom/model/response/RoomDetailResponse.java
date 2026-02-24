package com.finalProject.BookingMeetingRoom.model.response;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class RoomDetailResponse {
 /*   private String userId;
      private String userName;
  */
    private String roomId;
    private String locationCode;
    private RoomStatus status;
    private String currentUserId;
    private String currentUserName;
    private LocalDateTime checkInTime;
}
