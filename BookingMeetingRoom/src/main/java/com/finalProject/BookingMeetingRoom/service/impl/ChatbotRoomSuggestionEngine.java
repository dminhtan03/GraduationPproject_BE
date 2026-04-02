package com.finalProject.BookingMeetingRoom.service.impl;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.common.enums.RoomStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ChatbotRoomSuggestionEngine {

    private final RoomRepository roomRepository;
    private final ReservationRepository reservationRepository;

    public List<Room> suggest(Room originalRoom, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        if (originalRoom == null) return List.of();

        List<ReservationStatus> blockingStatuses = List.of(
                ReservationStatus.PENDING,
                ReservationStatus.RESERVED,
                ReservationStatus.IN_USE
        );

        Set<String> conflictingRoomIds = new HashSet<>(
                reservationRepository.findConflictingRoomIds(blockingStatuses, startTime, endTime)
        );

        List<Room> allRooms = roomRepository.findAllWithDetails();

        Integer targetCapacity = originalRoom.getCapacity();
        String targetBuildingId = originalRoom.getFloor() != null && originalRoom.getFloor().getBuilding() != null
                ? originalRoom.getFloor().getBuilding().getId()
                : null;
        String targetFloorId = originalRoom.getFloor() != null ? originalRoom.getFloor().getId() : null;

        Set<String> targetAmenities = normalizeAmenities(originalRoom.getAmenities());

        return allRooms.stream()
                .filter(r -> r != null && r.getId() != null)
                .filter(r -> !r.getId().equals(originalRoom.getId()))
                .filter(r -> r.getStatus() != RoomStatus.BROKEN)
                .filter(r -> !conflictingRoomIds.contains(r.getId()))
                .filter(r -> {
                    if (targetCapacity == null || r.getCapacity() == null) return true;
                    return r.getCapacity() >= Math.max(1, targetCapacity - 2); // allow a small downgrade if needed
                })
                .sorted((a, b) -> Double.compare(
                        score(b, targetCapacity, targetBuildingId, targetFloorId, targetAmenities),
                        score(a, targetCapacity, targetBuildingId, targetFloorId, targetAmenities)
                ))
                .limit(Math.max(0, limit))
                .collect(Collectors.toList());
    }

    private double score(Room candidate,
                         Integer targetCapacity,
                         String targetBuildingId,
                         String targetFloorId,
                         Set<String> targetAmenities) {

        double buildingScore = 0;
        double floorScore = 0;

        String candidateBuildingId = candidate.getFloor() != null && candidate.getFloor().getBuilding() != null
                ? candidate.getFloor().getBuilding().getId()
                : null;
        String candidateFloorId = candidate.getFloor() != null ? candidate.getFloor().getId() : null;

        if (targetBuildingId != null && targetBuildingId.equals(candidateBuildingId)) buildingScore = 1;
        if (targetFloorId != null && targetFloorId.equals(candidateFloorId)) floorScore = 1;

        int capacityDiff = 0;
        boolean capacityBelowTarget = false;
        if (targetCapacity != null && candidate.getCapacity() != null) {
            capacityDiff = Math.abs(candidate.getCapacity() - targetCapacity);
            capacityBelowTarget = candidate.getCapacity() < targetCapacity;
        }

        Set<String> candidateAmenities = normalizeAmenities(candidate.getAmenities());
        double amenityScore = overlapRatio(targetAmenities, candidateAmenities);

        double score = 0;
        score += amenityScore * 100.0;
        score += buildingScore * 25.0;
        score += floorScore * 10.0;
        score -= capacityDiff * 0.5;
        if (capacityBelowTarget) score -= 25.0;

        return score;
    }

    private Set<String> normalizeAmenities(List<Amenity> amenities) {
        if (amenities == null || amenities.isEmpty()) return Set.of();
        return amenities.stream()
                .map(Amenity::getName)
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private double overlapRatio(Set<String> a, Set<String> b) {
        if ((a == null || a.isEmpty()) && (b == null || b.isEmpty())) return 0.0;
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return 0.0;

        int intersection = 0;
        for (String x : a) {
            if (b.contains(x)) intersection++;
        }
        return (double) intersection / (double) Math.max(a.size(), 1);
    }
}
