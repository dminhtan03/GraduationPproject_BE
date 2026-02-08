package com.finalProject.BookingMeetingRoom.model.entity;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_room")
public class Room {

    @Id
    private String id;

    @Column(name = "LOCATION_CODE")
    private String locationCode;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    @Column(name = "SCORE")
    private Double score;

    @Column(name = "CREATED_AT")
    private LocalDateTime createAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "floor_id")
    private Floor floor;

    @OneToMany(mappedBy = "room")
    private List<Reservation> reservations;

    public Double getScore() {
        if (reservations == null || reservations.isEmpty()) {
            return 0.0;
        }

        var feedbackList = reservations.stream()
                .map(Reservation::getFeedback)
                .filter(feedback -> feedback != null)
                .toList();

        if (feedbackList.isEmpty()) {
            return 0.0;
        }

        double avgScore = feedbackList.stream()
                .mapToInt(Feedback::getRating)
                .average()
                .orElse(0.0);

        return Math.round(avgScore * 10.0) / 10.0;
    }

}
