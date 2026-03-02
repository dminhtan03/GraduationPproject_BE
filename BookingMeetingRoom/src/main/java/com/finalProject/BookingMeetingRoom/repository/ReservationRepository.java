package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.entity.User;
import com.finalProject.BookingMeetingRoom.model.projection.MyReservationProjection;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {



    @Query(value = """
            SELECT * FROM tbl_reservation r
            WHERE r.room_id = :roomId
            AND NOT (:endTime <= r.start_time OR :startTime >= r.end_time)
            """, nativeQuery = true)
    List<Reservation> checkOverlappingReservationsByRoom(@Param("roomId") String roomId,
                                                         @Param("startTime") LocalDateTime startTime,
                                                         @Param("endTime") LocalDateTime endTime);

    @Query(value = "SELECT * FROM tbl_reservation r " +
            " WHERE r.user_id = :userId " +
            " AND r.status in :status " +
            "AND NOT (:endTime <= r.start_time OR :startTime >= r.end_time)",
            nativeQuery = true)
    List<Reservation> checkOverlapByUser(@Param("userId") String userId,
                                         @Param("startTime") LocalDateTime startTime,
                                         @Param("endTime") LocalDateTime endTime,
                                         @Param("status") List<String> status);

    @Query(value = """
                SELECT * FROM tbl_reservation r
                WHERE r.room_id = :roomId
                  AND DATE(r.start_time ) = DATE(:startTime)
                  AND (
                       r.start_time < :endTime AND r.end_time > :startTime
            
                  )
            """, nativeQuery = true)
    List<Reservation> findOverlappingReservations(@Param("roomId") String roomId,
                                                  @Param("startTime") LocalDateTime startTime,
                                                  @Param("endTime") LocalDateTime endTime);


    @Query(nativeQuery = true, value = """
    SELECT
        tr.id AS reservationId,
        ts.location_code AS locationCode,
        tb.address AS address,
        tf.floor_name AS floorName,
        tb.building_name AS buildingName,
        tr.status AS reservationStatus,
        DATE(tr.start_time) AS selectedDate,
        tr.start_time AS startTime,
        tr.end_time AS endTime,

        TIMESTAMPDIFF(MINUTE, tr.start_time, tr.end_time) AS duration,

        CASE
          WHEN tr.status = 'COMPLETED'
                                    AND NOT EXISTS (
                                        SELECT 1
                                        FROM tbl_feedback tf
                                        WHERE tf.reservation_id = tr.id
                                    )
                               THEN FALSE
                               ELSE TRUE
                               END AS isFeedback

    FROM tbl_reservation tr
    JOIN tbl_user tu ON tu.id = tr.user_id
    JOIN tbl_room ts ON ts.id = tr.room_id
    JOIN tbl_floor tf ON tf.id = ts.floor_id
    JOIN tbl_building tb ON tb.id = tf.building_id

    WHERE tu.id = :userId
      AND (:locationCode IS NULL OR ts.location_code LIKE CONCAT('%', :locationCode, '%'))
      AND (:address IS NULL OR tb.address LIKE CONCAT('%', :address, '%'))
      AND (:buildingId IS NULL OR tb.id = :buildingId)
      AND (:statuses IS NULL OR tr.status IN (:statuses))

      AND (
            :startTime IS NULL
            OR (
                tr.start_time >= :startTime
                AND (:endTime IS NULL OR tr.end_time <= :endTime)
            )
          )

    ORDER BY tr.updated_at DESC
    """,
            countQuery = """
        SELECT COUNT(*)
        FROM tbl_reservation tr
        JOIN tbl_user tu ON tu.id = tr.user_id
        JOIN tbl_room ts ON ts.id = tr.room_id
        JOIN tbl_floor tf ON tf.id = ts.floor_id
        JOIN tbl_building tb ON tb.id = tf.building_id

        WHERE tu.id = :userId
          AND (:locationCode IS NULL OR ts.location_code LIKE CONCAT('%', :locationCode, '%'))
          AND (:address IS NULL OR tb.address LIKE CONCAT('%', :address, '%'))
          AND (:buildingId IS NULL OR tb.id = :buildingId)
          AND (:statuses IS NULL OR tr.status IN (:statuses))

          AND (
                :startTime IS NULL
                OR (
                    tr.start_time >= :startTime
                    AND (:endTime IS NULL OR tr.end_time <= :endTime)
                )
              )
    """
    )
    Page<MyReservationProjection> findMyReservations(
            @Param("userId") String userId,
            @Param("locationCode") String locationCode,
            @Param("address") String address,
            @Param("statuses") List<String> statuses,
            @Param("buildingId") String buildingId,
            @Param("startTime") String startTime,
            @Param("endTime") String endTime,
            Pageable pageable
    );

}