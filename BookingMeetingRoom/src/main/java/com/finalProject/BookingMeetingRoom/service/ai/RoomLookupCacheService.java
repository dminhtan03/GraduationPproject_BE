package com.finalProject.BookingMeetingRoom.service.ai;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finalProject.BookingMeetingRoom.model.dto.RoomLookupItem;
import com.finalProject.BookingMeetingRoom.model.projection.RoomMapDashboardProjection;
import com.finalProject.BookingMeetingRoom.repository.BuildingRepository;
import com.finalProject.BookingMeetingRoom.service.RedisService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoomLookupCacheService {

    private static final String CACHE_KEY = "ai:rag:room-lookup:v1";

    private final RedisService redisService;
    private final BuildingRepository buildingRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai.rag.refresh-ms:600000}")
    private long refreshMs;

    public List<RoomLookupItem> getRooms() {
        Object cached = redisService.getValue(CACHE_KEY);
        if (cached != null) {
            return objectMapper.convertValue(cached, new TypeReference<List<RoomLookupItem>>() {});
        }

        return refreshCacheInternal();
    }

    @Scheduled(fixedDelayString = "${ai.rag.refresh-ms:600000}")
    public void refreshCache() {
        refreshCacheInternal();
    }

    private List<RoomLookupItem> refreshCacheInternal() {
        List<RoomMapDashboardProjection> projections = buildingRepository.findRoomMapDashBoard();
        List<RoomLookupItem> items = projections.stream()
                .map(this::mapToItem)
                .collect(Collectors.toList());

        redisService.setValue(CACHE_KEY, items, refreshMs, TimeUnit.MILLISECONDS);
        return items;
    }

    private RoomLookupItem mapToItem(RoomMapDashboardProjection projection) {
        return RoomLookupItem.builder()
                .roomId(projection.getRoomId())
                .locationCode(projection.getLocationCode())
                .buildingId(projection.getBuildingId())
                .buildingName(projection.getBuildingName())
                .floorId(projection.getFloorId())
                .floorName(projection.getFloorName())
                .capacity(projection.getCapacity())
                .build();
    }
}
