package com.finalProject.BookingMeetingRoom.controller.room;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.model.request.FloorLayoutRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomCreateRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomSearchRequest;
import com.finalProject.BookingMeetingRoom.model.request.RoomUpdateRequest;
import com.finalProject.BookingMeetingRoom.model.response.RoomSearchResponse;
import com.finalProject.BookingMeetingRoom.service.RoomService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

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

    @GetMapping("/amenities")
    public ResponseEntity<?> getAllAmenities() {
        return ResponseEntity.ok(Response.ofSucceeded(roomService.getAllAmenities()));
    }

    // start add addRoom api
    @PostMapping
    public ResponseEntity<?> addRoom(@RequestBody @Valid RoomCreateRequest request) {
        roomService.addRoom(request);
        return ResponseEntity.ok(Response.ofSucceeded("Room added successfully"));
    }
    // end add addRoom api

    // start add updateRoom api
    @PutMapping
    public ResponseEntity<?> updateRoom(@RequestBody @Valid RoomUpdateRequest request) {
        roomService.updateRoom(request);
        return ResponseEntity.ok(Response.ofSucceeded("Room updated successfully"));
    }
    // end add updateRoom api

    // start add importRoomsFromExcel api
    @PostMapping("/import-excel")
    public ResponseEntity<?> importRoomsFromExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "floorId", required = false) String floorId) {
        roomService.importRoomsFromExcel(file, floorId);
        return ResponseEntity.ok(Response.ofSucceeded("Rooms imported successfully from excel"));
    }
    // end add importRoomsFromExcel api

    // start add updateFloorLayout api
    @PutMapping("/floors/{floorId}/layout")
    public ResponseEntity<?> updateFloorLayout(
            @PathVariable String floorId,
            @RequestBody FloorLayoutRequest request) {
        roomService.updateFloorLayout(floorId, request);
        return ResponseEntity.ok(Response.ofSucceeded("Floor layout updated successfully"));
    }
    // end add updateFloorLayout api
}
