package com.finalProject.BookingMeetingRoom.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class FileUploadController {

    private final Cloudinary cloudinary;

    @PostMapping(value = "/api/v1/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap("folder", "task_attachments", "resource_type", "auto")
            );
            String url = (String) result.get("secure_url");
            String name = file.getOriginalFilename();
            return ResponseEntity.ok(Response.ofSucceeded(Map.of("url", url, "name", name != null ? name : "file")));
        } catch (Exception e) {
            log.error("File upload failed: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Response.ofSucceeded(Map.of("error", e.getMessage())));
        }
    }
}
