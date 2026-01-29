package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "tbl_permission")
public class Permission {
    @Id
    private String id;

    @Column(name = "PERMISSION_NAME")
    private String name;
}
