package com.finalProject.BookingMeetingRoom.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.finalProject.BookingMeetingRoom.model.entity.FloorDecoration;

@Repository
public interface FloorDecorationRepository extends JpaRepository<FloorDecoration, String> {
    List<FloorDecoration> findByFloorId(String floorId);

    @Modifying
    @Transactional
    @Query("DELETE FROM FloorDecoration d WHERE d.floor.id = :floorId")
    void deleteByFloorId(String floorId);
}
