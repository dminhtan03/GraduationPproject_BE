package com.finalProject.BookingMeetingRoom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.common.enums.ServiceItemStatus;
import com.finalProject.BookingMeetingRoom.model.entity.ReservationServiceItem;

@Repository
public interface ReservationServiceItemRepository extends JpaRepository<ReservationServiceItem, String> {
    // start+ chức năng dịch vụ đi kèm (truy vấn theo reservation)
    List<ReservationServiceItem> findByReservation_Id(String reservationId);
    void deleteByReservation_Id(String reservationId);
    // start+ chức năng bảo toàn lịch sử DONE/CANCELLED
    List<ReservationServiceItem> findByReservation_IdAndStatusNotIn(String reservationId, List<ServiceItemStatus> statuses);
    // end+ chức năng bảo toàn lịch sử DONE/CANCELLED
    // end+ chức năng dịch vụ đi kèm (truy vấn theo reservation)
}
