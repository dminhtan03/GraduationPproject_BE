package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "tbl_user_otp")
public class UserOtp {

    @Id
    private String id;

    @Column(name = "OTP_CODE", nullable = false, length = 6)
    private String otpCode;

    @Column(name = "ISSUED_AT", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "EXPIRES_AT", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "VALIDATED_AT")
    private LocalDateTime validatedAt;

    @Column(name = "IS_USED", nullable = false)
    private boolean isUsed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", referencedColumnName = "ID", nullable = false)
    private User user;

}