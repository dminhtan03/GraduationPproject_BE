package com.finalProject.BookingMeetingRoom.ai.util;

import com.finalProject.BookingMeetingRoom.common.enums.ChatbotIntent;
import com.finalProject.BookingMeetingRoom.common.utils.ChatbotMessageParser;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotMessageParserTest {

    private final ChatbotMessageParser parser = new ChatbotMessageParser();

    @Test
    void shouldDetectAvailableRoomsTodayIntent_vi() {
        var r = parser.parse("Hôm nay còn phòng trống không?");
        assertEquals(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY, r.intent());
    }

    @Test
    void shouldParseBooking_en_singleTimeDefaultsToOneHour() {
        var r = parser.parse("Book room AL-102 at 10AM today");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals("AL-102", r.roomCode());
        assertEquals(LocalDate.now(), r.date());
        assertNotNull(r.startTime());
        assertNotNull(r.endTime());
        assertTrue(r.endTimeDefaulted());
        assertEquals(10, r.startTime().getHour());
        assertEquals(0, r.startTime().getMinute());
        assertEquals(11, r.endTime().getHour());
    }

    @Test
    void shouldParseBooking_vi_rangeTime() {
        var r = parser.parse("Giúp tôi đặt phòng A-203 từ 14h đến 15h hôm nay");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals("A-203", r.roomCode());
        assertEquals(LocalDate.now(), r.date());
        assertEquals(14, r.startTime().getHour());
        assertEquals(15, r.endTime().getHour());
        assertFalse(r.endTimeDefaulted());
    }

    @Test
    void shouldParseBooking_en_roomCodeWithDigitsInPrefix() {
        var r = parser.parse("Book V5-020 at 15PM today");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals("V5-020", r.roomCode());
        assertEquals(LocalDate.now(), r.date());
        assertNotNull(r.startTime());
        assertNotNull(r.endTime());
        assertEquals(15, r.startTime().getHour());
        assertEquals(16, r.endTime().getHour());
        assertTrue(r.endTimeDefaulted());
    }

    @Test
    void shouldParseAvailableRoomsAsOfTime_today() {
        var r = parser.parse("Rooms are available as of 6 PM today.");
        assertEquals(ChatbotIntent.CHECK_AVAILABLE_ROOMS_TODAY, r.intent());
        assertEquals(LocalDate.now(), r.date());
        assertNotNull(r.startTime());
        assertEquals(18, r.startTime().getHour());
    }

    @Test
    void shouldParseBooking_en_tomorrow_singleTime() {
        var r = parser.parse("Book AL-102 at 10AM tomorrow");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals("AL-102", r.roomCode());
        assertEquals(LocalDate.now().plusDays(1), r.date());
        assertNotNull(r.startTime());
        assertEquals(10, r.startTime().getHour());
    }

    @Test
    void shouldParseBooking_en_tomorow_typo_and_vi_ngay_mai() {
        var en = parser.parse("Book AL-102 at 10AM tomorow");
        assertEquals(LocalDate.now().plusDays(1), en.date());

        var vi = parser.parse("Đặt AL-102 lúc 10h ngày mai");
        assertEquals(LocalDate.now().plusDays(1), vi.date());
    }

    @Test
    void shouldNotTreatCapacityAsRoomCode() {
        var r = parser.parse("Book a room with a capacity of 20 people for tomorrow.");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals(LocalDate.now().plusDays(1), r.date());
        assertNull(r.roomCode());
    }

    @Test
    void shouldDetectSuggestRoomsByCapacityIntent_and_parseMinCapacity() {
        var r = parser.parse("Suggest rooms that can accommodate 20 or more people.");
        assertEquals(ChatbotIntent.SUGGEST_ROOMS_BY_CAPACITY, r.intent());
        assertEquals(20, r.minCapacity());
        assertNull(r.roomCode());
    }

    @Test
    void shouldParseBooking_en_tomorrow_atRangeTime() {
        var r = parser.parse("Book me room V21-024 for tomorrow at 6PM to 8PM");
        assertEquals(ChatbotIntent.BOOK_ROOM, r.intent());
        assertEquals("V21-024", r.roomCode());
        assertEquals(LocalDate.now().plusDays(1), r.date());
        assertNotNull(r.startTime());
        assertNotNull(r.endTime());
        assertEquals(18, r.startTime().getHour());
        assertEquals(20, r.endTime().getHour());
        assertFalse(r.endTimeDefaulted());
    }
}
