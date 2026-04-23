package com.finalProject.BookingMeetingRoom.model.response;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng check-in bằng QR (token response)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckInQrTokenResponse {
    private String token;
    private LocalDateTime expiresAt;
}
// end+ chức năng check-in bằng QR (token response)
