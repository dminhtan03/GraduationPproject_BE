package com.finalProject.BookingMeetingRoom.service;

import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RoomImageService {
    RoomImageResponse uploadImage(String roomId, MultipartFile file);
    RoomImageResponse replaceImage(String imageId, MultipartFile file);
    void deleteImage(String imageId);
    List<RoomImageResponse> getImagesByRoom(String roomId);
}