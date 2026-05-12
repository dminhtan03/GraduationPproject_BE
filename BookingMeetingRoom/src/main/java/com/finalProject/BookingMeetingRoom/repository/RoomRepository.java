package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.projection.RoomDtoProjection;
import com.finalProject.BookingMeetingRoom.model.projection.RoomResponseProjection;
// start add imports
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
// end add imports
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, String> {

    // start add method for pessimistic lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Room r WHERE r.id = :id")
    Optional<Room> findByIdForUpdate(@Param("id") String id);
    // end add method for pessimistic lock

    @Query(nativeQuery = true, value = """
            SELECT ts.id as roomId,
                   ts.location_code as locationCode,
                   ts.status,
                   ts.score,
                   ts.x_position as xPosition,
                   ts.y_position as yPosition,
                   ts.width as width,
                   ts.height as height,
                   ts.is_positioned as positioned
            FROM tbl_room ts
            JOIN tbl_floor tf ON ts.floor_id = tf.id
            WHERE tf.id = :floorId
            AND tf.is_deleted = false
            ORDER BY locationCode
            """)
    List<RoomDtoProjection> findRooms(String floorId);

    List<Room> findByFloorOrderByLocationCode(Floor floor);

    Page<Room> findByFloorOrderByLocationCode(Floor floor, Pageable pageable);

    List<Room> findByFloor(Floor floor);

        @EntityGraph(attributePaths = {"floor", "floor.building", "amenities"})
    @Query("SELECT r FROM Room r")
    List<Room> findAllWithDetails();

        @EntityGraph(attributePaths = {"floor", "floor.building", "amenities"})
    @Query("SELECT r FROM Room r WHERE r.floor.building.id IN :buildingIds")
    List<Room> findAllWithDetailsByBuildingIds(@Param("buildingIds") List<String> buildingIds);

        @EntityGraph(attributePaths = {"floor", "floor.building", "amenities"})
    Optional<Room> findByLocationCodeIgnoreCase(String locationCode);

        @EntityGraph(attributePaths = {"floor", "floor.building"})
        Optional<Room> findWithFloorAndBuildingById(String id);

    boolean existsByFloorIdAndLocationCode(String floorId, String locationCode);
    Optional<Room> findByLocationCode(String locationCode);

    boolean existsByLocationCode(String locationCode);

    @Query("SELECT r.locationCode FROM Room r WHERE r.floor.id = :floorId")
    List<String> findLocationCodesByFloorId(@Param("floorId") String floorId);

    @Query("SELECT r.locationCode FROM Room r")
    List<String> findAllLocationCodes();

    int countByStatus(RoomStatus roomStatus);

    @Query(nativeQuery = true, value = """
            SELECT *
            FROM tbl_room
            WHERE id IN (:roomIds);
            """)
    List<Room> findRoomsByRoomIds(List<String> roomIds);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) AS occupiedRooms
            FROM tbl_room
            WHERE status = 'UNAVAILABLE'
            """)
    int countOccupiedRooms();

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) AS brokenRooms
            FROM tbl_room
            WHERE status = 'BROKEN'
            """)
    int countBrokenRooms();

    interface CurrentUserProjection {
        String getUserId();
        String getUserName();
        LocalDateTime getCheckInTime();
    }

    @Query("""
            SELECT u.id as userId,
                   CONCAT(ui.firstName, ' ', ui.lastName) as userName,
                   r.checkinTime as checkInTime
            FROM Reservation r
            JOIN r.room ro
            JOIN r.user u
            JOIN u.userInfo ui
            WHERE ro.id = :roomId
              AND r.returnTime IS NULL
              AND r.status IN (
                    'RESERVED',
                    'IN_USE'
              )
              AND r.startTime = (
                  SELECT MAX(r2.startTime)
                  FROM Reservation r2
                  WHERE r2.room.id = :roomId
                    AND r2.returnTime IS NULL
                    AND r2.status IN (
                          'RESERVED',
                          'IN_USE'
                    )
              )
            """)
    CurrentUserProjection findCurrentUserByRoomId(String roomId);
}