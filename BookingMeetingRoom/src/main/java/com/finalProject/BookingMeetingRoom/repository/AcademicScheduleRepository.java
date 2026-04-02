package com.finalProject.BookingMeetingRoom.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.finalProject.BookingMeetingRoom.model.entity.AcademicSchedule;

@Repository
public interface AcademicScheduleRepository extends JpaRepository<AcademicSchedule, String> {

    @Query("SELECT s FROM AcademicSchedule s WHERE s.room.id = :roomId " +
            "AND s.fromDate <= :date AND s.toDate >= :date")
    List<AcademicSchedule> findSchedulesByRoomAndDate(String roomId, LocalDate date);

    List<AcademicSchedule> findByRoomId(String roomId);
}