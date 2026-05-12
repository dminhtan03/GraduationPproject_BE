package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.request.FloorLayoutRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomContextResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import java.time.LocalDateTime;

import com.finalProject.BookingMeetingRoom.model.entity.FloorDecoration;

public interface RoomService {

    List<RoomSearchResponse> searchRooms(RoomSearchRequest request);

    List<RoomSearchResponse> searchRandomAvailableRooms(List<String> buildingIds, LocalDateTime startTime, LocalDateTime endTime, int limit);

    Page<RoomSearchResponse> getRoomStatus(RoomSearchRequest request, int page, int size);

    RoomDetailResponse getRoomDetail(String roomId);

    RoomSearchResponse findRoomByLocationCode(String locationCode);

    RoomContextResponse getRoomContextByLocationCode(String locationCode);

    RoomContextResponse getRoomContextByRoomId(String roomId);

    List<Amenity> getAllAmenities();

    // start add addRoom method
    void addRoom(RoomCreateRequest request, MultipartFile image);
    // end add addRoom method

    // start add updateRoom method
    void updateRoom(RoomUpdateRequest request);
    // end add updateRoom method

    // start add importRoomsFromExcel method
    void importRoomsFromExcel(MultipartFile file, String floorId);
    // end add importRoomsFromExcel method

    // start add updateFloorLayout method
    void updateFloorLayout(String floorId, FloorLayoutRequest request);
    // end add updateFloorLayout method

    // start add getDecorationsByFloorId method
    List<FloorDecoration> getDecorationsByFloorId(String floorId);
    // end add getDecorationsByFloorId method
}
