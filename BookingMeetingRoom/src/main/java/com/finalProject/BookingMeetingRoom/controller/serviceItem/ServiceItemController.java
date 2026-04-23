package com.finalProject.BookingMeetingRoom.controller.serviceItem;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.finalProject.BookingMeetingRoom.common.exception.CustomException;
import com.finalProject.BookingMeetingRoom.common.payload.Response;
import com.finalProject.BookingMeetingRoom.common.payload.ResponseCode;
import com.finalProject.BookingMeetingRoom.model.entity.ServiceItem;
import com.finalProject.BookingMeetingRoom.model.request.ServiceItemRequest;
import com.finalProject.BookingMeetingRoom.repository.ServiceItemRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

// start+ chức năng CRUD dịch vụ đi kèm (ServiceItem)
@RestController
@RequestMapping("/api/v1/service-items")
@RequiredArgsConstructor
public class ServiceItemController {

    private final ServiceItemRepository serviceItemRepository;

    @GetMapping
    public ResponseEntity<?> getServiceItems(@RequestParam(name = "activeOnly", defaultValue = "false") boolean activeOnly) {
        List<ServiceItem> items = serviceItemRepository.findAll();
        if (activeOnly) {
            items = items.stream()
                    .filter(i -> Boolean.TRUE.equals(i.getActive()))
                    .sorted(Comparator.comparing(ServiceItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } else {
            items = items.stream()
                    .sorted(Comparator.comparing(ServiceItem::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        return ResponseEntity.ok(Response.ofSucceeded(items));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createServiceItem(@RequestBody @Valid ServiceItemRequest request) {
        if (serviceItemRepository.existsByNameIgnoreCase(request.getName())) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Service item name already exists");
        }
        if (request.getPrice() != null && request.getPrice() < 0) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Price must be >= 0");
        }

        ServiceItem item = new ServiceItem();
        item.setId(UUID.randomUUID().toString());
        item.setName(request.getName().trim());
        item.setDescription(request.getDescription());
        item.setUnit(request.getUnit());
        item.setPrice(request.getPrice());
        item.setActive(request.getActive() == null ? Boolean.TRUE : request.getActive());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        serviceItemRepository.save(item);
        return ResponseEntity.ok(Response.ofSucceeded("Service item created successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateServiceItem(@PathVariable String id, @RequestBody @Valid ServiceItemRequest request) {
        ServiceItem item = serviceItemRepository.findById(id)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Service item not found"));

        String nextName = request.getName().trim();
        if (!item.getName().equalsIgnoreCase(nextName) && serviceItemRepository.existsByNameIgnoreCase(nextName)) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Service item name already exists");
        }
        if (request.getPrice() != null && request.getPrice() < 0) {
            throw new CustomException(ResponseCode.VALIDATION_FAILED, "Price must be >= 0");
        }

        item.setName(nextName);
        item.setDescription(request.getDescription());
        item.setUnit(request.getUnit());
        item.setPrice(request.getPrice());
        if (request.getActive() != null) {
            item.setActive(request.getActive());
        }
        item.setUpdatedAt(LocalDateTime.now());

        serviceItemRepository.save(item);
        return ResponseEntity.ok(Response.ofSucceeded("Service item updated successfully"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteServiceItem(@PathVariable String id) {
        ServiceItem item = serviceItemRepository.findById(id)
                .orElseThrow(() -> new CustomException(ResponseCode.VALIDATION_FAILED, "Service item not found"));

        item.setActive(Boolean.FALSE);
        item.setUpdatedAt(LocalDateTime.now());
        serviceItemRepository.save(item);
        return ResponseEntity.ok(Response.ofSucceeded("Service item deactivated successfully"));
    }
}
// end+ chức năng CRUD dịch vụ đi kèm (ServiceItem)
