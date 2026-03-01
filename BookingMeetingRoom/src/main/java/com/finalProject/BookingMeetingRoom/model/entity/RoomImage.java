package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_room_image")
public class RoomImage {

    @Id
    private String id;

    @Column(name = "IMAGE_URL")
    private String imageUrl;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;
}