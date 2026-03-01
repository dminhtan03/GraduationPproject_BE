package com.finalProject.BookingMeetingRoom.repository;

import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.dto.RoomDto;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.projection.RoomDtoProjection;
import com.finalProject.BookingMeetingRoom.model.projection.RoomResponseProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface RoomRepository extends JpaRepository<Room, String> {

    @Query(nativeQuery = true, value = """
            SELECT ts.id as roomId,
                   ts.location_code as locationCode,
                   ts.status,
                   ts.score
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
            AND r.checkinTime = (
                SELECT MAX(r2.checkinTime)
                FROM Reservation r2
                WHERE r2.room.id = :roomId
            )
            """)
    CurrentUserProjection findCurrentUserByRoomId(String roomId);
}