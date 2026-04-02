package com.finalProject.BookingMeetingRoom.controller.amenity;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.request.AmenityRequest;
import com.finalProject.BookingMeetingRoom.repository.AmenityRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/amenities")
@RequiredArgsConstructor
public class AmenityController {

    private final AmenityRepository amenityRepository;

    @GetMapping
    public ResponseEntity<?> getAllAmenities() {
        List<Amenity> amenities = amenityRepository.findAll();
        return ResponseEntity.ok(Response.ofSucceeded(amenities));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAmenity(@RequestBody @Valid AmenityRequest request) {
        if (amenityRepository.existsByName(request.getName())) {
            throw new CustomException(ResponseCode.AMENITY_ALREADY_EXISTS);
        }

        Amenity amenity = new Amenity();
        amenity.setId(UUID.randomUUID().toString());
        amenity.setName(request.getName());
        amenity.setCreatedAt(LocalDateTime.now());
        amenity.setUpdatedAt(LocalDateTime.now());

        amenityRepository.save(amenity);
        return ResponseEntity.ok(Response.ofSucceeded("Amenity created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateAmenity(@PathVariable String id, @RequestBody @Valid AmenityRequest request) {
        Amenity amenity = amenityRepository.findById(id)
                .orElseThrow(() -> new CustomException(ResponseCode.AMENITY_NOT_FOUND));

        if (!amenity.getName().equalsIgnoreCase(request.getName()) && amenityRepository.existsByName(request.getName())) {
            throw new CustomException(ResponseCode.AMENITY_ALREADY_EXISTS);
        }

        amenity.setName(request.getName());
        amenity.setUpdatedAt(LocalDateTime.now());

        amenityRepository.save(amenity);
        return ResponseEntity.ok(Response.ofSucceeded("Amenity updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAmenity(@PathVariable String id) {
        if (!amenityRepository.existsById(id)) {
            throw new CustomException(ResponseCode.AMENITY_NOT_FOUND);
        }
        
        try {
            amenityRepository.deleteById(id);
        } catch (Exception e) {
            throw new CustomException(ResponseCode.INTERNAL_SERVER_ERROR, "Cannot delete amenity as it is being used by some rooms");
        }
        
        return ResponseEntity.ok(Response.ofSucceeded("Amenity deleted successfully"));
    }
}
