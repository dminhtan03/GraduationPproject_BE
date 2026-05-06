package com.finalProject.BookingMeetingRoom.service.ai;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.finalProject.BookingMeetingRoom.model.dto.RoomLookupItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagRoomResolver {

    private static final int MAX_CONTEXT_ROOMS = 5;

    private static final Pattern LOCATION_CODE_PATTERN = Pattern.compile("\\b([A-Z]{1,4}\\d{1,4}-\\d{1,4})\\b", Pattern.CASE_INSENSITIVE);
        private static final Pattern CAPACITY_PATTERN = Pattern.compile(
            "\\b(\\d{1,3})\\s*(cho|seat|seats|people|person|persons|pax)\\b|capacity\\s*(of)?\\s*(\\d{1,3})",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern BUILDING_PATTERN = Pattern.compile("\\b(toa nha|building)\\s+([a-z0-9_-]+)\\b", Pattern.CASE_INSENSITIVE);

    private final RoomLookupCacheService cacheService;

    public RagResult resolve(String message) {
        if (!StringUtils.hasText(message)) {
            return RagResult.empty();
        }

        String normalized = normalize(message);
        String locationCode = extractLocationCode(normalized);
        Integer capacity = extractCapacity(normalized);
        String buildingToken = extractBuildingToken(normalized);

        List<RoomLookupItem> rooms = cacheService.getRooms();
        RoomLookupItem exactRoom = null;
        if (StringUtils.hasText(locationCode)) {
            exactRoom = rooms.stream()
                    .filter(r -> r.getLocationCode() != null && r.getLocationCode().equalsIgnoreCase(locationCode))
                    .findFirst()
                    .orElse(null);
        }

        List<RoomLookupItem> candidates = new ArrayList<>();
        if ((capacity != null && capacity > 0) || StringUtils.hasText(buildingToken)) {
            candidates = rooms.stream()
                    .filter(r -> capacity == null || r.getCapacity() == null || r.getCapacity() >= capacity)
                    .filter(r -> !StringUtils.hasText(buildingToken) || containsToken(r.getBuildingName(), buildingToken))
                    .sorted(Comparator.comparing(RoomLookupItem::getLocationCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                    .limit(MAX_CONTEXT_ROOMS)
                    .toList();
        }

        return new RagResult(locationCode, capacity, buildingToken, exactRoom, candidates);
    }

    private String normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private String extractLocationCode(String normalized) {
        Matcher matcher = LOCATION_CODE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private Integer extractCapacity(String normalized) {
        Matcher matcher = CAPACITY_PATTERN.matcher(normalized);
        if (matcher.find()) {
            if (matcher.group(1) != null) {
                return Integer.parseInt(matcher.group(1));
            }
            if (matcher.group(4) != null) {
                return Integer.parseInt(matcher.group(4));
            }
        }
        return null;
    }

    private String extractBuildingToken(String normalized) {
        Matcher matcher = BUILDING_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group(2).toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private boolean containsToken(String text, String token) {
        if (!StringUtils.hasText(text) || !StringUtils.hasText(token)) {
            return false;
        }
        String normalized = normalize(text);
        return normalized.contains(token.toLowerCase(Locale.ROOT));
    }

    public record RagResult(
            String locationCode,
            Integer capacity,
            String buildingToken,
            RoomLookupItem exactRoom,
            List<RoomLookupItem> candidates
    ) {
        public static RagResult empty() {
            return new RagResult(null, null, null, null, List.of());
        }

        public boolean hasContext() {
            return exactRoom != null || (candidates != null && !candidates.isEmpty());
        }
    }
}
