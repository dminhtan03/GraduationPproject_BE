package com.finalProject.BookingMeetingRoom.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "tbl_floor_decoration")
public class FloorDecoration {
    @Id
    private String id;

    @JsonIgnore
    @ManyToOne
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    @Column(name = "DECOR_TYPE", nullable = false)
    private String type; // LOBBY, HALLWAY, STAIRS, ELEVATOR

    @Column(name = "LABEL")
    private String label;

    @Column(name = "X_POSITION")
    private double x;

    @Column(name = "Y_POSITION")
    private double y;

    @Column(name = "WIDTH")
    private double width;

    @Column(name = "HEIGHT")
    private double height;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        createdAt = LocalDateTime.now();
    }
}
