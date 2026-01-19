package com.finalProject.BookingMeetingRoom.model.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Builder
@Table(name = "tbl_refresh_token")
public class RefreshToken {

    @Id
    private String id;

    @Column(name = "REFRESH_TOKEN", length = 1000)
    private String token;

    @Column(name = "EXPIRES_AT")
    private LocalDateTime expiresAt;

    @Column(name = "CREATE_AT")
    private LocalDateTime createdAt;

    @Column(name = "IS_REVOKED")
    private boolean isRevoked;

    @ManyToOne
    @JoinColumn(name = "user_id", referencedColumnName = "id")
    @JsonBackReference
    private User user;
}
