package com.finalProject.BookingMeetingRoom.model.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.finalProject.BookingMeetingRoom.common.enums.ServiceItemStatus;

// start+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng (ReservationServiceItem entity)
@Getter
@Setter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tbl_reservation_service_item")
public class ReservationServiceItem {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "RESERVATION_ID", nullable = false)
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SERVICE_ITEM_ID", nullable = false)
    private ServiceItem serviceItem;

    @Column(name = "QUANTITY")
    private Integer quantity;

    @Column(name = "NOTE")
    private String note;

    @Column(name = "PRICE_SNAPSHOT")
    private Double priceSnapshot;

    @Column(name = "STATUS")
    @Enumerated(EnumType.STRING)
    private ServiceItemStatus status;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;
}
// end+ chức năng đặt thêm dịch vụ đi kèm khi đặt phòng (ReservationServiceItem entity)
