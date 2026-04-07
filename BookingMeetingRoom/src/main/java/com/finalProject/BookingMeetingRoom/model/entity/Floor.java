package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
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
@Table(name = "tbl_floor")
public class Floor {
    @Id
    private String id;

    @Column(name = "FLOOR_NAME")
    private String name;

    @Column(name = "IS_DELETED")
    private boolean isDeleted;

    @Column(name = "CREATED_AT")
    private LocalDateTime createAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "building_id")
    private Building building;

    @OneToMany(mappedBy = "floor", cascade = CascadeType.ALL)
    private List<Room> seats;
}
