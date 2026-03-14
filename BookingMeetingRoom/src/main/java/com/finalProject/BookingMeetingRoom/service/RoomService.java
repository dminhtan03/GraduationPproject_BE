package com.finalProject.BookingMeetingRoom.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;

public interface RoomService {

    List<RoomSearchResponse> searchRooms(RoomSearchRequest request);

    Page<RoomSearchResponse> getRoomStatus(RoomSearchRequest request, int page, int size);

    RoomDetailResponse getRoomDetail(String roomId);

    // start add addRoom method
    void addRoom(RoomCreateRequest request);
    // end add addRoom method

    // start add importRoomsFromExcel method
    void importRoomsFromExcel(MultipartFile file);
    // end add importRoomsFromExcel method
}
