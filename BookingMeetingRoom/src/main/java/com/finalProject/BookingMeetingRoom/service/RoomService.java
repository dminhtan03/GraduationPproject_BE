package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import org.springframework.data.domain.Page;

import java.util.List;

public interface RoomService {

    List<RoomSearchResponse> searchRooms(RoomSearchRequest request);

    Page<RoomSearchResponse> getRoomStatus(RoomSearchRequest request, int page, int size);

    RoomDetailResponse getRoomDetail(String roomId);
}
