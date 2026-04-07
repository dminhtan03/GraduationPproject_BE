package com.finalProject.BookingMeetingRoom.ai.service;

import com.finalProject.BookingMeetingRoom.common.enums.ReservationStatus;
import com.finalProject.BookingMeetingRoom.model.entity.Amenity;
import com.finalProject.BookingMeetingRoom.model.entity.Building;
import com.finalProject.BookingMeetingRoom.model.entity.Floor;
import com.finalProject.BookingMeetingRoom.model.entity.Room;
import com.finalProject.BookingMeetingRoom.repository.ReservationRepository;
import com.finalProject.BookingMeetingRoom.repository.RoomRepository;
import com.finalProject.BookingMeetingRoom.service.impl.ChatbotRoomSuggestionEngine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatbotRoomSuggestionEngineTest {

    @Test
    void shouldPreferSameBuildingAndAmenityOverlap() {
        RoomRepository roomRepository = mock(RoomRepository.class);
        ReservationRepository reservationRepository = mock(ReservationRepository.class);

        ChatbotRoomSuggestionEngine engine = new ChatbotRoomSuggestionEngine(roomRepository, reservationRepository);

        Building b1 = new Building(); b1.setId("B1"); b1.setName("Alpha");
        Building b2 = new Building(); b2.setId("B2"); b2.setName("Beta");

        Floor f1 = new Floor(); f1.setId("F1"); f1.setName("1"); f1.setBuilding(b1);
        Floor f2 = new Floor(); f2.setId("F2"); f2.setName("2"); f2.setBuilding(b1);
        Floor f3 = new Floor(); f3.setId("F3"); f3.setName("3"); f3.setBuilding(b2);

        Amenity projector = new Amenity(); projector.setId("A1"); projector.setName("Projector");
        Amenity tv = new Amenity(); tv.setId("A2"); tv.setName("TV");
        Amenity whiteboard = new Amenity(); whiteboard.setId("A3"); whiteboard.setName("Whiteboard");

        Room original = new Room();
        original.setId("R0");
        original.setLocationCode("AL-102");
        original.setCapacity(8);
        original.setFloor(f1);
        original.setAmenities(List.of(projector, tv));

        Room sameBuildingHighOverlap = new Room();
        sameBuildingHighOverlap.setId("R1");
        sameBuildingHighOverlap.setLocationCode("AL-105");
        sameBuildingHighOverlap.setCapacity(8);
        sameBuildingHighOverlap.setFloor(f2);
        sameBuildingHighOverlap.setAmenities(List.of(projector, tv, whiteboard));

        Room otherBuildingOverlap = new Room();
        otherBuildingOverlap.setId("R2");
        otherBuildingOverlap.setLocationCode("B-201");
        otherBuildingOverlap.setCapacity(8);
        otherBuildingOverlap.setFloor(f3);
        otherBuildingOverlap.setAmenities(List.of(projector, tv));

        when(reservationRepository.findConflictingRoomIds(anyList(), any(), any()))
                .thenReturn(List.of());
        when(roomRepository.findAllWithDetails())
                .thenReturn(List.of(original, sameBuildingHighOverlap, otherBuildingOverlap));

        var res = engine.suggest(original, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2), 5);

        assertFalse(res.isEmpty());
        assertEquals("R1", res.get(0).getId());
    }
}
