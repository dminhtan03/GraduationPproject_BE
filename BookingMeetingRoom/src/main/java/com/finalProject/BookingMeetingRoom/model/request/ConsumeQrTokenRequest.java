package com.finalProject.BookingMeetingRoom.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// start+ chức năng check-in bằng QR (consume token request)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeQrTokenRequest {
    @NotBlank
    private String token;
}
// end+ chức năng check-in bằng QR (consume token request)
