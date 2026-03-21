package com.finalProject.BookingMeetingRoom.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Reservation;
import com.finalProject.BookingMeetingRoom.model.projection.MyReservationProjection;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, String> {

  Optional<Reservation> findByIdAndStatus(String id, ReservationStatus status);

  @Query("SELECT COUNT(r) > 0 FROM Reservation r " +
      "WHERE r.room.id = :roomId " +
      "AND r.status IN :status " +
      "AND :startTime < r.endTime AND :endTime > r.startTime " + // overlap logic
      "AND (:currentReservationId IS NULL OR r.id <> :currentReservationId)")
  boolean checkOverlapReservation(@Param("roomId") String roomId,
      @Param("status") List<ReservationStatus> status,
      @Param("startTime") LocalDateTime startTime,
      @Param("endTime") LocalDateTime endTime,
      @Param("currentReservationId") String currentReservationId);

  @Query(value = "SELECT COALESCE(SUM(TIMESTAMPDIFF(MINUTE, r.start_time, r.end_time)), 0) " +
      "FROM tbl_reservation r " +
      "WHERE r.user_id = :userId " +
      "AND DATE(r.start_time) = :date " +
      "AND r.status IN ('RESERVED', 'IN_USE', 'COMPLETED')", nativeQuery = true)
  long getTotalReservedMinutesForUser(@Param("userId") String userId,
      @Param("date") LocalDate date);

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
      "AND NOT (:endTime <= r.start_time OR :startTime >= r.end_time)", nativeQuery = true)
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
          tr.purpose AS purpose,
          tr.note AS note,
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
        AND (tr.status IN (:statuses))

        AND (
              :startTime IS NULL
              OR (
                  tr.start_time >= :startTime
                  AND (:endTime IS NULL OR tr.end_time <= :endTime)
              )
            )

      ORDER BY tr.updated_at DESC
      """, countQuery = """
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
            AND (tr.status IN (:statuses))

            AND (
                  :startTime IS NULL
                  OR (
                      tr.start_time >= :startTime
                      AND (:endTime IS NULL OR tr.end_time <= :endTime)
                  )
                )
      """)
  Page<MyReservationProjection> findMyReservations(
      @Param("userId") String userId,
      @Param("locationCode") String locationCode,
      @Param("address") String address,
      @Param("statuses") List<String> statuses,
      @Param("buildingId") String buildingId,
      @Param("startTime") String startTime,
      @Param("endTime") String endTime,
      Pageable pageable);

  // start update findReservationsOverStartTime to use parameter
  @Query(value = """
      SELECT *
      FROM tbl_reservation r
      WHERE DATE_ADD(r.start_time, INTERVAL 15 MINUTE) < :currentTime
        AND r.status = 'RESERVED'
      """, nativeQuery = true)
  List<Reservation> findReservationsOverStartTime(@Param("currentTime") LocalDateTime currentTime);
  // end update findReservationsOverStartTime to use parameter

  // start update findReservationsOverEndTime to use parameter
  @Query(value = "SELECT * from tbl_reservation r WHERE r.end_time < :currentTime " +
      " and r.status = 'IN_USE' ", nativeQuery = true)
  List<Reservation> findReservationsOverEndTime(@Param("currentTime") LocalDateTime currentTime);
  // end update findReservationsOverEndTime to use parameter

  List<Reservation> findByStatus(ReservationStatus status);

  @Query("SELECT r FROM Reservation r WHERE r.user.id IN :userIds AND r.status IN :statuses")
  List<Reservation> findByUserIdsAndStatusIn(Set<String> userIds, List<ReservationStatus> statuses);

  @Query("SELECT r FROM Reservation r " +
      "WHERE r.room.id IN :roomIds " +
      "AND r.status IN :statuses")
  List<Reservation> findByRoomIdsAndStatusIn(@Param("roomIds") Set<String> roomIds,
      @Param("statuses") List<ReservationStatus> statuses);

  @Query("""
      SELECT u.id,
             COUNT(r)
      FROM User u
      LEFT JOIN u.reservations r
             ON r.startTime >= :startOfDay
             AND r.startTime < :endOfDay
             AND r.status <> 'FAIL'
      WHERE u.id IN :userIds
      GROUP BY u.id
      """)
  List<Object[]> countReservationsTodayByUserIds(@Param("userIds") Set<String> userIds,
      @Param("startOfDay") LocalDateTime startOfDay,
      @Param("endOfDay") LocalDateTime endOfDay);

  // start update findReservationsToRemindCheckIn to use parameter
  @Query(value = """
      SELECT *
      FROM tbl_reservation r
      WHERE r.start_time BETWEEN
            DATE_ADD(:currentTime, INTERVAL 15 MINUTE)
            AND DATE_ADD(:currentTime, INTERVAL 16 MINUTE)
        AND r.status = 'RESERVED'
      """, nativeQuery = true)
  List<Reservation> findReservationsToRemindCheckIn(@Param("currentTime") LocalDateTime currentTime);
  // end update findReservationsToRemindCheckIn to use parameter

  @Query(value = """
      SELECT *
      FROM tbl_reservation
      WHERE end_time <= :time
        AND room_id = :roomId
        AND status IN ('RESERVED', 'IN_USE')
      ORDER BY end_time DESC
      LIMIT 1
      """, nativeQuery = true)
  Optional<Reservation> findLastReservation(
      @Param("time") LocalDateTime time,
      @Param("roomId") String roomId);

  @Query(value = """
      SELECT *
      FROM tbl_reservation
      WHERE start_time >= :time
        AND room_id = :roomId
        AND status IN ('RESERVED', 'IN_USE')
      ORDER BY start_time ASC
      LIMIT 1
      """, nativeQuery = true)
  Optional<Reservation> findNextReservation(
      @Param("time") LocalDateTime time,
      @Param("roomId") String roomId);

}