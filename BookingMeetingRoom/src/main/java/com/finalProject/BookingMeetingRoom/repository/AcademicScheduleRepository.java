package com.finalProject.BookingMeetingRoom.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;

@Repository
public interface AcademicScheduleRepository extends JpaRepository<AcademicSchedule, String> {

    @Query("SELECT s FROM AcademicSchedule s WHERE s.room.id = :roomId " +
            "AND s.fromDate <= :date AND s.toDate >= :date")
    List<AcademicSchedule> findSchedulesByRoomAndDate(String roomId, LocalDate date);

    @Query("SELECT s FROM AcademicSchedule s WHERE s.room.id = :roomId " +
            "AND s.fromDate <= :toDate AND s.toDate >= :fromDate " +
            "AND s.startTime < :endTime AND s.endTime > :startTime")
    List<AcademicSchedule> findPotentialOverlappingSchedules(
            @Param("roomId") String roomId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime);

    List<AcademicSchedule> findByRoomId(String roomId);

    @Query("SELECT s FROM AcademicSchedule s WHERE s.fromDate <= :date AND s.toDate >= :date")
    List<AcademicSchedule> findAllActiveSchedulesToday(@Param("date") LocalDate date);

    @Query("SELECT s FROM AcademicSchedule s " +
            "LEFT JOIN s.room r " +
            "LEFT JOIN r.floor f " +
            "LEFT JOIN f.building b " +
            "WHERE (:roomName IS NULL OR :roomName = '' OR LOWER(r.locationCode) LIKE LOWER(CONCAT('%', :roomName, '%'))) " +
            "AND (:floorId IS NULL OR :floorId = '' OR f.id = :floorId) " +
            "AND (:buildingId IS NULL OR :buildingId = '' OR b.id = :buildingId) " +
            "AND (:fromDate IS NULL OR s.fromDate >= :fromDate) " +
            "AND (:toDate IS NULL OR s.toDate <= :toDate) " +
            "ORDER BY s.createdAt DESC")
    Page<AcademicSchedule> searchSchedules(
            @Param("roomName") String roomName,
            @Param("floorId") String floorId,
            @Param("buildingId") String buildingId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);
}