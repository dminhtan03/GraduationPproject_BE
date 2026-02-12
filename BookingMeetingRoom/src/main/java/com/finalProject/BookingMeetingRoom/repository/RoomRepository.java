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
            SELECT tu.id as userId,
                   CONCAT(tui.first_name, ' ', tui.last_name) as userName,
                   tr.checkin_time as checkInTime
            FROM tbl_room ts
            JOIN tbl_reservation tr ON ts.id = tr.room_id
            JOIN tbl_user tu ON tr.user_id = tu.id
            JOIN tbl_user_info tui ON tu.user_info_id = tui.id
            WHERE ts.id = :roomId
              AND ts.status = 'UNAVAILABLE'
              AND tr.checkin_time = (
                SELECT MAX(checkin_time)
                FROM tbl_reservation
                WHERE room_id = :roomId
            );
            """)
    RoomResponseProjection findRoomInMap(String roomId);

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) AS occupiedSeats
            FROM tbl_room
            WHERE status = 'UNAVAILABLE'
            """)
    int countOccupiedSeats();

    @Query(nativeQuery = true, value = """
            SELECT COUNT(*) AS brokenSeats
            FROM tbl_room
            WHERE status = 'BROKEN'
            """)
    int countBrokenSeats();

}