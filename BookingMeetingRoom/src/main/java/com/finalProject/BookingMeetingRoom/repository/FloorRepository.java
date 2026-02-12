package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.dto.AdminFloorDto;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousFloorResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FloorRepository extends JpaRepository<Floor, String> {

    @Query(nativeQuery = true, value = """
            SELECT tf.id, tf.floor_name as floorName, tf.building_id
            FROM tbl_floor tf
            WHERE tf.building_id = :buildingId
            AND tf.is_deleted = false
            """)
    Page<AdminFloorDto> findAllByBuildingIdAndDeleted(String buildingId, Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT tf.id, tf.floor_name as floorName, tf.building_id
            FROM tbl_floor tf
            WHERE tf.building_id = :buildingId
            AND tf.is_deleted = false
            """)
    List<AdminFloorDto> findFloorByBuildingIdAndDeleted(String buildingId);

    @Query(nativeQuery = true, value = """
            SELECT tf.id,
                   tf.floor_name as floorName,
                   tf.building_id
            FROM tbl_floor tf
            WHERE tf.id = :floorId
            AND tf.is_deleted = false
            """)
    Optional<AdminFloorDto> findFloor(String floorId);

    @Query(nativeQuery = true, value = """
            SELECT tf.*
            FROM tbl_floor tf
            WHERE tf.id = :floorId
            AND tf.is_deleted = false
            """)
    Optional<Floor> findByIdAndDeleted(String floorId);

    @Query(nativeQuery = true, value = """
            SELECT tf.id as id,
                tf.floor_name as name
            FROM tbl_floor tf
            WHERE tf.is_deleted = false
            AND tf.building_id = :buildingId
            """)
    List<AmbiguousFloorResponse> findAllFloorsByBuildingId(String buildingId);

}