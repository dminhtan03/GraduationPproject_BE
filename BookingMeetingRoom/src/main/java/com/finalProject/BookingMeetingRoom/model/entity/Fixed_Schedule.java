package com.finalProject.BookingMeetingRoom.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "tbl_fixed_schedule",
        indexes = {
                @Index(name = "idx_fixed_schedule_room", columnList = "room_id"),
                @Index(name = "idx_fixed_schedule_room_day", columnList = "room_id, day_of_week")
        })
@AllArgsConstructor
@NoArgsConstructor
public class Fixed_Schedule {
    @Id
    private String id;

    @ManyToOne
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "day_of_week", nullable = false, length = 3)
    private DayOfWeek dayOfWeek;
    // MON, TUE, ...

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;
    // 07:00

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;
    // 09:00

    @Column(name = "effective_from", nullable = false)
    private LocalDateTime effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    private LocalDateTime effectiveTo;

}
