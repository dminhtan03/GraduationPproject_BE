package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_room_image",
        indexes = {
                @Index(name = "idx_room_image_room_id", columnList = "room_id"),
                @Index(name = "idx_room_image_public_id", columnList = "PUBLIC_ID")
        })
public class RoomImage {

    @Id
    private String id;

    @Column(name = "IMAGE_URL")
    private String imageUrl;

    // Cloudinary public_id để delete/replace
    @Column(name = "PUBLIC_ID")
    private String publicId;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;
}