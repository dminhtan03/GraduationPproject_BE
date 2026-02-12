package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.model.dto.AdminBuildingDto;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.projection.BuildingOccupancyProjection;
import com.finalProject.BookingMeetingRoom.model.projection.RoomMapDashboardProjection;
import com.finalProject.BookingMeetingRoom.model.response.AmbiguousBuildingResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BuildingRepository extends JpaRepository<Building, String> {

    @Query(nativeQuery = true, value = """
            WITH floor_count AS (
                SELECT
                    building_id,
                    CAST(COUNT(*) AS INT) AS numsOfFloors
                FROM tbl_floor
                WHERE is_deleted = false
                GROUP BY building_id
            )
            SELECT
                tb.id,
                tb.building_name AS name,
                tb.address,
                COALESCE(floor_count.numsOfFloors, 0) AS totalFloors
            FROM tbl_building tb
            LEFT JOIN floor_count ON tb.id = floor_count.building_id
            WHERE tb.is_deleted = false
            ORDER BY tb.created_at DESC;
            """)
    Page<AdminBuildingDto> findAllBuilding(Pageable pageable);

    @Query(nativeQuery = true, value = """
            SELECT tb.id as buildingId,
                   tb.building_name as buildingName,
                   tb.address,
                   tf.id as floorId,
                   tf.floor_name as floorName,
                   ts.id as roomId,
                   ts.location_code as locationCode,
                   ts.status as status,
                   ts.score as score
            FROM tbl_building tb
            JOIN tbl_floor tf on tb.id = tf.building_id
            JOIN tbl_room ts on tf.id = ts.floor_id
            WHERE tb.is_deleted = false
              AND tf.is_deleted = false
            """)
    List<RoomMapDashboardProjection> findRoomMapDashBoard();

    Building findByIdAndIsDeleted(String buildingId, boolean isDeleted);

    @Query(nativeQuery = true, value = """
            SELECT
                b.building_name AS buildingName,
                COUNT(s.id) FILTER (WHERE s.status = 'UNAVAILABLE') AS occupied,
                COUNT(s.id) AS totalRooms,
                COUNT(s.id) FILTER (WHERE s.status = 'BROKEN') AS brokenRooms,
                COUNT(s.id) FILTER (WHERE s.status = 'AVAILABLE') AS availableRooms,
                COALESCE(ROUND(
                    100.0 * COUNT(s.id) FILTER (WHERE s.status = 'UNAVAILABLE') / NULLIF(COUNT(s.id), 0)
                ), 0) AS occupancyRate
            FROM tbl_building b
            LEFT JOIN tbl_floor f ON b.id = f.building_id AND f.is_deleted = false
            LEFT JOIN tbl_room s ON f.id = s.floor_id
            WHERE b.is_deleted = false
            GROUP BY b.building_name
            LIMIT 5;
            """)
    List<BuildingOccupancyProjection> findBuildingOccupancy();


    @Query(nativeQuery = true, value = """
            
            SELECT
                tb.id as id,
                tb.building_name AS name,
                tb.address as address
            FROM tbl_building tb
            WHERE tb.is_deleted = false
            """)
    List<AmbiguousBuildingResponse> findAllBuildings();
}