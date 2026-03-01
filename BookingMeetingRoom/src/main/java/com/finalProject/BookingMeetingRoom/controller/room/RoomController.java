package com.finalProject.BookingMeetingRoom.controller.room;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomDetailResponse;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/search")
    public ResponseEntity<List<RoomSearchResponse>> searchRooms(@RequestBody @Valid RoomSearchRequest request) {
        return ResponseEntity.ok(roomService.searchRooms(request));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getRoomStatus(@RequestBody @Valid RoomSearchRequest request,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(Response.ofSucceeded(roomService.getRoomStatus(request, page, size)));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomDetail(@PathVariable String roomId) {
        return ResponseEntity.ok(Response.ofSucceeded(roomService.getRoomDetail(roomId)));
    }
}
