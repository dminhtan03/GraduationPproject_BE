package com.finalProject.BookingMeetingRoom.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.model.entity.RoomImage;
import com.finalProject.BookingMeetingRoom.model.response.RoomImageResponse;
import com.finalProject.BookingMeetingRoom.repository.RoomImageRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.RoomImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomImageServiceImpl implements RoomImageService {

    private final Cloudinary cloudinary;
    private final RoomImageRepository roomImageRepository;
    private final RoomRepository roomRepository;

    @Override
    @Transactional
    public RoomImageResponse uploadImage(String roomId, MultipartFile file) {
        validateFile(file);

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        try {
            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "meeting-room/" + roomId,
                            "resource_type", "image"
                    )
            );

            String imageUrl = uploadResult.get("secure_url").toString();
            String publicId = uploadResult.get("public_id").toString();

            RoomImage roomImage = new RoomImage();
            roomImage.setId(UUID.randomUUID().toString());
            roomImage.setImageUrl(imageUrl);
            roomImage.setPublicId(publicId);
            roomImage.setCreatedAt(LocalDateTime.now());
            roomImage.setRoom(room);

            roomImageRepository.save(roomImage);

            return mapToResponse(roomImage);

        } catch (IOException e) {
            throw new RuntimeException("Upload image to Cloudinary failed", e);
        }
    }

    @Override
    @Transactional
    public RoomImageResponse replaceImage(String imageId, MultipartFile file) {
        validateFile(file);

        RoomImage existingImage = roomImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("RoomImage not found: " + imageId));

        try {
            // 1. Xóa ảnh cũ trên cloud nếu có
            if (existingImage.getPublicId() != null && !existingImage.getPublicId().isBlank()) {
                cloudinary.uploader().destroy(
                        existingImage.getPublicId(),
                        ObjectUtils.asMap("resource_type", "image")
                );
            }

            // 2. Upload ảnh mới
            String roomId = existingImage.getRoom().getId();

            Map<?, ?> uploadResult = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder", "meeting-room/" + roomId,
                            "resource_type", "image"
                    )
            );

            existingImage.setImageUrl(uploadResult.get("secure_url").toString());
            existingImage.setPublicId(uploadResult.get("public_id").toString());
            existingImage.setCreatedAt(LocalDateTime.now());

            roomImageRepository.save(existingImage);

            return mapToResponse(existingImage);

        } catch (IOException e) {
            throw new RuntimeException("Replace image failed", e);
        }
    }

    @Override
    @Transactional
    public void deleteImage(String imageId) {
        RoomImage roomImage = roomImageRepository.findById(imageId)
                .orElseThrow(() -> new RuntimeException("RoomImage not found: " + imageId));

        try {
            if (roomImage.getPublicId() != null && !roomImage.getPublicId().isBlank()) {
                cloudinary.uploader().destroy(
                        roomImage.getPublicId(),
                        ObjectUtils.asMap("resource_type", "image")
                );
            }

            roomImageRepository.delete(roomImage);

        } catch (IOException e) {
            throw new RuntimeException("Delete image on Cloudinary failed", e);
        }
    }

    @Override
    public List<RoomImageResponse> getImagesByRoom(String roomId) {
        return roomImageRepository.findByRoomId(roomId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null ||
                !(contentType.equals("image/jpeg")
                        || contentType.equals("image/png")
                        || contentType.equals("image/jpg")
                        || contentType.equals("image/webp"))) {
            throw new RuntimeException("Only JPG, JPEG, PNG, WEBP allowed");
        }
    }

    private RoomImageResponse mapToResponse(RoomImage roomImage) {
        return RoomImageResponse.builder()
                .id(roomImage.getId())
                .imageUrl(roomImage.getImageUrl())
                .publicId(roomImage.getPublicId())
                .createdAt(roomImage.getCreatedAt())
                .roomId(roomImage.getRoom().getId())
                .build();
    }
}