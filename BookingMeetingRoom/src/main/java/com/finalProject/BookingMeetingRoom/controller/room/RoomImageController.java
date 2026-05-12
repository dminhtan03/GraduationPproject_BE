package com.finalProject.BookingMeetingRoom.controller.room;

import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import com.finalProject.BookingMeetingRoom.service.RoomImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/room-images")
@RequiredArgsConstructor
public class RoomImageController {

    private final RoomImageService roomImageService;

    // Upload ảnh mới cho room
    @PostMapping(value = "/upload/{roomId}", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<RoomImageResponse> uploadImage(
            @PathVariable String roomId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(roomImageService.uploadImage(roomId, file));
    }

    // Replace ảnh theo imageId
    @PutMapping(value = "/replace/{imageId}", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<RoomImageResponse> replaceImage(
            @PathVariable String imageId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(roomImageService.replaceImage(imageId, file));
    }

    // Xóa ảnh
    @DeleteMapping("/{imageId}")
    @PreAuthorize("hasAnyAuthority(@authorityConstant.ADMIN)")
    public ResponseEntity<String> deleteImage(@PathVariable String imageId) {
        roomImageService.deleteImage(imageId);
        return ResponseEntity.ok("Delete image successfully");
    }

    // Lấy danh sách ảnh theo room
    @GetMapping("/room/{roomId}")
    public ResponseEntity<List<RoomImageResponse>> getImagesByRoom(@PathVariable String roomId) {
        return ResponseEntity.ok(roomImageService.getImagesByRoom(roomId));
    }
}