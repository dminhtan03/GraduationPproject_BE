package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tbl_amenity")
public class Amenity {

    @Id
    private String id;

    @Column(name = "NAME")
    private String name;
}