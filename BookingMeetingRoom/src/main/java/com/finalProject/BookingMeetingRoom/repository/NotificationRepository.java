package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    @Query(value = "SELECT * FROM tbl_notification WHERE " +
            "user_id = :userId " +
            "ORDER BY created_at DESC",
            nativeQuery = true
    )
    Page<Notification> findAllByUser(@Param("userId") String userId,
                                     Pageable pageable);
}
