package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// start+ chức năng CRUD dịch vụ đi kèm (ServiceItem entity)
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_service_item")
public class ServiceItem {

    @Id
    private String id;

    @Column(name = "NAME", nullable = false, unique = true)
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "UNIT")
    private String unit;

    @Column(name = "PRICE")
    private Double price;

    @Column(name = "ACTIVE")
    private Boolean active;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
// end+ chức năng CRUD dịch vụ đi kèm (ServiceItem entity)
