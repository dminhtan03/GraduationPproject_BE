package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_room")
public class Room {

    @Id
    private String id;

    @Column(name = "LOCATION_CODE", unique = true)
    private String locationCode;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    @Column(name = "CAPACITY")
    private Integer capacity;

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

    @ManyToMany
    @JoinTable(
            name = "tbl_room_amenity",
            joinColumns = @JoinColumn(name = "room_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    private List<Amenity> amenities;

    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<RoomImage> images;

    // start add layout fields
    @Column(name = "X_POSITION")
    private Double xPosition = 0.0;

    @Column(name = "Y_POSITION")
    private Double yPosition = 0.0;

    @Column(name = "WIDTH")
    private Double width = 100.0;

    @Column(name = "HEIGHT")
    private Double height = 100.0;

    @Column(name = "IS_POSITIONED")
    private Boolean positioned = false;
    // end add layout fields

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
